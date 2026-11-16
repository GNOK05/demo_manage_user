"""Paired evaluation of seven scenarios, four sizes, ten methods; CSV + JSON."""
import argparse
import csv
import hashlib
import json
from pathlib import Path
from time import perf_counter
import numpy as np
from scipy.stats import friedmanchisquare
from .environment import CVRPEnv, SCENARIOS, scenario_config
from .solvers import BASELINES, LEARNED, solve

SIZES = ((20, 8), (50, 8), (100, 12), (200, 15))


def summarize(rows, methods):
    summaries, tests = [], []
    groups = sorted({(r['scenario'], r['customers'], r['grid']) for r in rows})
    for scenario, n, grid in groups:
        block = [r for r in rows if (r['scenario'], r['customers'], r['grid']) == (scenario, n, grid)]
        paired = []
        for seed in sorted({r['seed'] for r in block}):
            sample = {r['method']: r for r in block if r['seed'] == seed}
            if all(m in sample and sample[m]['complete'] for m in methods):
                paired.append([sample[m]['cost'] for m in methods])
        values = np.array(paired, dtype=float).reshape(-1, len(methods))
        z = ((values-values.mean(1, keepdims=True)) /
             np.maximum(values.std(1, keepdims=True), 1e-12)) if len(values) else values
        for i, method in enumerate(methods):
            sample = [r for r in block if r['method'] == method]
            complete_costs = np.array([r['cost'] for r in sample if r['complete']])
            summaries.append(dict(scenario=scenario, customers=n, grid=grid, method=method,
                instances=len(sample), completion_rate=float(np.mean([r['complete'] for r in sample])),
                cost_mean=float(complete_costs.mean()) if len(complete_costs) else None,
                cost_se=float(complete_costs.std(ddof=1)/np.sqrt(len(complete_costs))) if len(complete_costs)>1 else None,
                runtime_mean=float(np.mean([r['seconds'] for r in sample])),
                failures_mean=float(np.mean([r['failures'] for r in sample])),
                paired_complete_instances=len(values),
                paired_mean_z=float(z[:, i].mean()) if len(z) else None))
        test = dict(scenario=scenario, customers=n, grid=grid, paired_complete_instances=len(values),
                    note='Omnibus Friedman only; does not establish pairwise superiority.')
        if len(methods) >= 3 and len(values) >= 2 and np.any(values != values[:, :1]):
            statistic, pvalue = friedmanchisquare(*values.T)
            test.update(statistic=float(statistic), pvalue=float(pvalue))
        else:
            test.update(statistic=None, pvalue=None, reason='insufficient non-tied complete paired blocks')
        tests.append(test)
    return summaries, tests


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--checkpoint', type=Path)
    parser.add_argument('--instances', type=int, default=100)
    parser.add_argument('--scenarios', nargs='+', choices=tuple(SCENARIOS), default=list(SCENARIOS))
    parser.add_argument('--sizes', nargs='+', type=int, choices=(20, 50, 100, 200), default=[20, 50, 100, 200])
    parser.add_argument('--methods', nargs='+', choices=BASELINES+LEARNED, default=list(BASELINES))
    parser.add_argument('--seed', type=int, default=2025)
    parser.add_argument('--device', default='cpu')
    parser.add_argument('--threads', type=int, default=2)
    parser.add_argument('--deterministic', action='store_true', help='argmax instead of paper sampling')
    parser.add_argument('--output', type=Path, default=Path('runs/evaluation'))
    args = parser.parse_args()
    if args.instances < 1:
        parser.error('instances must be positive')
    if len(set(args.methods)) != len(args.methods):
        parser.error('methods must be unique')
    agent = None
    torch = None
    if any(m in LEARNED for m in args.methods):
        if args.checkpoint is None:
            parser.error('learned methods require --checkpoint; no random weights are silently used')
        import torch
        from .model import RoutingAgent
        torch.set_num_threads(args.threads)
        agent = RoutingAgent().to(args.device)
        saved = torch.load(args.checkpoint, map_location=args.device, weights_only=False)
        agent.load_state_dict(saved['model'])
        agent.eval()
    args.output.mkdir(parents=True, exist_ok=True)
    settings = {k: str(v) if isinstance(v, Path) else v for k, v in vars(args).items()}
    if agent is not None:
        settings['checkpoint_episode'] = saved['episode']
        settings['checkpoint_sha256'] = hashlib.sha256(args.checkpoint.read_bytes()).hexdigest()
        settings['implementation'] = saved['implementation']
    (args.output/'settings.json').write_text(json.dumps(settings, indent=2), encoding='utf-8')
    rows = []
    with (args.output/'routes.jsonl').open('w', encoding='utf-8') as routes:
        for scenario in args.scenarios:
            for customers, grid in SIZES:
                if customers not in args.sizes:
                    continue
                for idx in range(args.instances):
                    seed = args.seed+idx
                    for method in args.methods:
                        env = CVRPEnv(scenario_config(scenario, customers=customers, grid_size=grid), seed)
                        if torch is not None:
                            torch.manual_seed(seed)
                        if agent is not None and args.device.startswith('cuda'):
                            torch.cuda.synchronize()
                        start = perf_counter()
                        result = solve(env, method, agent, args.device, args.deterministic)
                        if agent is not None and args.device.startswith('cuda'):
                            torch.cuda.synchronize()
                        elapsed = perf_counter()-start
                        labels = dict(scenario=scenario, customers=customers, grid=grid,
                                      seed=seed, method=method, seconds=elapsed)
                        routes.write(json.dumps(dict(**labels, result=result,
                                                     coords=env.coords.tolist(),
                                                     customer_nodes=env.customers.tolist()))+'\n')
                        rows.append(dict(**labels, **{k: v for k, v in result.items()
                                                      if k not in ('route', 'step_costs', 'customers')}))
                print(f'{scenario}: N={customers} done', flush=True)
    with (args.output/'instances.csv').open('w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    summaries, tests = summarize(rows, args.methods)
    with (args.output/'summary.csv').open('w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=list(summaries[0]))
        writer.writeheader()
        writer.writerows(summaries)
    (args.output/'friedman.json').write_text(json.dumps(tests, indent=2, allow_nan=False), encoding='utf-8')


if __name__ == '__main__':
    main()
