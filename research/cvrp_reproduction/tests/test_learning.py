import numpy as np
import pytest
import torch
from cvrp.environment import CVRPEnv, EnvConfig
from cvrp.model import RoutingAgent, tensors
from cvrp.solvers import solve
from cvrp.train import discounted_returns, ppo_episode, seed_all
from cvrp.evaluate import summarize

torch.set_num_threads(2)


def test_chunked_nnconv_matches_pyg_outputs_and_gradients():
    seed_all(4)
    model = RoutingAgent(edge_chunk=7)
    x = torch.randn(5, 6, requires_grad=True)
    costs = torch.rand(5, 5)
    src, dst = torch.where(~torch.eye(5, dtype=torch.bool))
    expected = model.conv1(x, torch.stack((src, dst)), costs[src, dst, None])
    actual = model._conv(model.conv1, x, costs)
    torch.testing.assert_close(actual, expected)
    g1 = torch.autograd.grad(expected.square().sum(), (x, *model.conv1.parameters()), retain_graph=True)
    g2 = torch.autograd.grad(actual.square().sum(), (x, *model.conv1.parameters()))
    for a, b in zip(g1, g2):
        torch.testing.assert_close(a, b, atol=2e-5, rtol=2e-5)


def test_recurrence_changes_action_distribution_and_memory_gradients():
    seed_all(7)
    agent = RoutingAgent().eval()
    env = CVRPEnv(EnvConfig(grid_size=3, customers=3))
    x, costs, mask = tensors(env, 'cpu')
    p0, v, h = agent(x, costs)
    p1, _, _ = agent(x, costs, torch.ones_like(h)*2)
    assert not torch.allclose(torch.softmax(p0, 0), torch.softmax(p1, 0), atol=1e-7, rtol=0)
    dist = agent.distribution(p0, mask)
    assert torch.all(dist.probs[~mask] == 0)
    (dist.log_prob(torch.tensor(int(np.flatnonzero(env.mask())[0])))+v).backward()
    assert sum(float(p.grad.abs().sum()) for p in agent.gru.parameters()) > 0


def test_discounted_returns():
    assert discounted_returns([1, 2, 3]) == [2.75, 3.5, 3.]


def test_ppo_update_and_checkpoint_roundtrip(tmp_path):
    seed_all(42)
    env = CVRPEnv(EnvConfig(grid_size=3, customers=3), seed=1)
    agent = RoutingAgent()
    before = {k: v.clone() for k, v in agent.state_dict().items()}
    metrics = ppo_episode(agent, torch.optim.Adam(agent.parameters(), lr=1e-4), env, 'cpu')
    assert np.isfinite(metrics['loss'])
    assert any(not torch.equal(before[k], v) for k, v in agent.state_dict().items())
    for name in ('conv1', 'conv2', 'transformer', 'gru', 'value'):
        assert any(not torch.equal(before[k], v) for k, v in agent.state_dict().items() if k.startswith(name))
    path = tmp_path/'weights.pt'
    torch.save(agent.state_dict(), path)
    restored = RoutingAgent()
    restored.load_state_dict(torch.load(path, weights_only=True))
    agent.eval()
    restored.eval()
    x, c, _ = tensors(env, 'cpu')
    torch.testing.assert_close(agent(x, c)[0], restored(x, c)[0])


@pytest.mark.parametrize('method', ['gnn', 'gnn_g', 'gnn_cw'])
def test_learned_rollout_has_accounted_costs(method):
    seed_all(3)
    env = CVRPEnv(EnvConfig(grid_size=3, customers=3), seed=4)
    result = solve(env, method, RoutingAgent())
    assert result['complete'] or result['truncated']
    assert result['cost'] == pytest.approx(sum(result['step_costs']))
    assert result['steps'] == len(result['route'])-1


def test_statistics_exclude_incomplete_costs_and_require_paired_blocks():
    rows = []
    for seed in range(3):
        for idx, method in enumerate(('a', 'b', 'c')):
            rows.append(dict(scenario='standard', customers=20, grid=8, seed=seed,
                             method=method, cost=10+idx+seed, complete=not(seed==2 and method=='a'),
                             seconds=1., failures=0))
    summaries, tests = summarize(rows, ['a', 'b', 'c'])
    assert tests[0]['paired_complete_instances'] == 2
    assert summaries[0]['completion_rate'] == pytest.approx(2/3)
    assert summaries[0]['cost_mean'] == 10.5
    assert tests[0]['pvalue'] is not None


@pytest.mark.parametrize('grid,customers', [(8, 50), (12, 100), (15, 200)])
def test_forward_at_reported_scales(grid, customers):
    env = CVRPEnv(EnvConfig(grid_size=grid, customers=customers))
    agent = RoutingAgent().eval()
    with torch.inference_mode():
        logits, value, memory = agent(*tensors(env, 'cpu')[:2])
    assert logits.shape == (grid**2,)
    assert torch.isfinite(logits).all() and torch.isfinite(value)
    assert memory.shape == (1, 1, 64)
