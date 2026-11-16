"""Episode-wise PPO: gamma=.5, clip=.2, 4 epochs, Adam 1e-4, grad norm 1."""
import argparse
from dataclasses import asdict
import json
from pathlib import Path
import random
import numpy as np
import torch
from .environment import CVRPEnv, EnvConfig
from .model import RoutingAgent, tensors


def seed_all(seed):
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


def discounted_returns(rewards, gamma=.5, bootstrap=0.):
    values = []
    running = bootstrap
    for reward in reversed(rewards):
        running = reward + gamma * running
        values.append(running)
    return list(reversed(values))


def ppo_episode(agent, optimizer, env, device, gamma=.5, epochs=4, clip=.2):
    # Dropout is zero, so rollout/replay use identical likelihoods.
    agent.eval()
    memory, records, rewards = None, [], []
    while not env.complete and not env.truncated:
        x, costs, mask = tensors(env, device)
        with torch.no_grad():
            logits, value, next_memory = agent(x, costs, memory)
            dist = agent.distribution(logits, mask)
            action = dist.sample()
        records.append((x, costs, mask, memory, action, dist.log_prob(action), value))
        _, reward, _, _ = env.step(action.item())
        rewards.append(reward)
        memory = next_memory.detach()
    # Paper has a finite step cap. Treat capped episodes as terminal and report
    # non-completion explicitly rather than hide it in low travel cost.
    returns = torch.tensor(discounted_returns(rewards, gamma), device=device)
    old_values = torch.stack([r[-1] for r in records])
    advantages = returns - old_values
    advantages = (advantages-advantages.mean()) / advantages.std(unbiased=False).clamp_min(1e-8)
    agent.train()
    losses = []
    # Memory inputs from rollout are detached: recurrent replay with a one-step
    # gradient horizon, avoiding a full episode's complete-graph computation.
    for _ in range(epochs):
        optimizer.zero_grad(set_to_none=True)
        epoch_loss = 0.
        for idx, (x, costs, mask, memory_in, action, old_logp, _) in enumerate(records):
            logits, value, _ = agent(x, costs, memory_in)
            dist = agent.distribution(logits, mask)
            ratio = (dist.log_prob(action)-old_logp).exp()
            surrogate = torch.minimum(ratio * advantages[idx],
                                      ratio.clamp(1-clip, 1+clip) * advantages[idx])
            loss = (-surrogate + .5*(value-returns[idx]).square() - .01*dist.entropy()) / len(records)
            if not torch.isfinite(loss):
                raise FloatingPointError('non-finite PPO loss')
            loss.backward()
            epoch_loss += float(loss.detach())
        torch.nn.utils.clip_grad_norm_(agent.parameters(), 1., error_if_nonfinite=True)
        optimizer.step()
        losses.append(epoch_loss)
    return dict(loss=float(np.mean(losses)), reward=sum(rewards), **env.result())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--episodes', type=int, default=200000)
    parser.add_argument('--grid-size', type=int, default=8)
    parser.add_argument('--min-customers', type=int, default=10)
    parser.add_argument('--max-customers', type=int, default=63)
    parser.add_argument('--seed', type=int, default=42)
    parser.add_argument('--device', default='cuda' if torch.cuda.is_available() else 'cpu')
    parser.add_argument('--output', type=Path, default=Path('runs/paper'))
    parser.add_argument('--resume', type=Path)
    parser.add_argument('--save-every', type=int, default=100)
    parser.add_argument('--threads', type=int, default=2)
    args = parser.parse_args()
    if not 1 <= args.min_customers <= args.max_customers < args.grid_size**2:
        parser.error('invalid customer range for grid')
    if args.episodes < 1 or args.save_every < 1:
        parser.error('episodes and save-every must be positive')
    torch.set_num_threads(args.threads)
    seed_all(args.seed)
    agent = RoutingAgent().to(args.device)
    optimizer = torch.optim.Adam(agent.parameters(), lr=1e-4)
    start = 0
    if args.resume:
        saved = torch.load(args.resume, map_location=args.device, weights_only=False)
        if saved['config']['grid_size'] != args.grid_size:
            parser.error('resume grid must match checkpoint')
        for key in ('seed', 'min_customers', 'max_customers'):
            if saved['settings'][key] != getattr(args, key):
                parser.error(f'resume {key} must match checkpoint settings')
        agent.load_state_dict(saved['model'])
        optimizer.load_state_dict(saved['optimizer'])
        start = saved['episode']
        torch.set_rng_state(saved['torch_rng'].cpu())
        if args.device.startswith('cuda') and saved.get('cuda_rng') is not None:
            torch.cuda.set_rng_state_all(saved['cuda_rng'])
    args.output.mkdir(parents=True, exist_ok=True)
    settings = {k: str(v) if isinstance(v, Path) else v for k, v in vars(args).items()}
    (args.output/'config.json').write_text(json.dumps(settings, indent=2), encoding='utf-8')
    for episode in range(start, args.episodes):
        # Episode-addressable seeds make resume independent of prior NumPy draws.
        rng = np.random.default_rng(np.random.SeedSequence([args.seed, episode]))
        count = int(rng.integers(args.min_customers, args.max_customers+1))
        config = EnvConfig(grid_size=args.grid_size, customers=count)
        env = CVRPEnv(config, seed=int(rng.integers(0, 2**32)))
        metrics = ppo_episode(agent, optimizer, env, args.device)
        metrics['episode'] = episode+1
        with (args.output/'training.jsonl').open('a', encoding='utf-8') as f:
            f.write(json.dumps(metrics)+'\n')
        print(f"episode={episode+1} loss={metrics['loss']:.3f} cost={metrics['cost']:.1f} "
              f"served={metrics['served']}/{count} complete={metrics['complete']}", flush=True)
        if (episode+1) % args.save_every == 0 or episode+1 == args.episodes:
            checkpoint = dict(model=agent.state_dict(), optimizer=optimizer.state_dict(),
                              episode=episode+1, config=asdict(config), settings=settings,
                              torch_rng=torch.get_rng_state(),
                              cuda_rng=torch.cuda.get_rng_state_all() if torch.cuda.is_available() else None,
                              implementation='paper-reconstruction-v1')
            temporary = args.output/'checkpoint.tmp'
            torch.save(checkpoint, temporary)
            temporary.replace(args.output/'checkpoint.pt')


if __name__ == '__main__':
    main()
