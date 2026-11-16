"""Seven baselines and two hybrid action masks, on one shared simulator."""
import numpy as np

BASELINES = ('tabu', 'greedy', 'random', 'largest_demand', 'max_reachable', 'cw', 'sweep')
LEARNED = ('gnn', 'gnn_g', 'gnn_cw')


def savings_candidates(env, k=3):
    feasible = np.flatnonzero(env.mask())
    feasible = feasible[feasible != 0]
    pairs = []
    d, c = env.known_demands(), env.costs
    for ix, i in enumerate(feasible):
        for j in feasible[ix+1:]:
            if d[i] + d[j] <= env.load:
                pairs.append((c[0, i] + c[0, j] - c[i, j], int(i), int(j)))
    selected, load = set(), 0.
    for _, i, j in sorted(pairs, reverse=True):
        if len(selected) >= k:
            break
        for node in (i, j):
            if node not in selected and load + d[node] <= env.load and len(selected) < k:
                selected.add(node)
                load += d[node]
    if not selected:
        selected.update(sorted(feasible, key=lambda i: (c[env.position, i], i))[:k])
    return sorted(selected)


def hybrid_mask(env, method, k=3):
    mask = env.mask()
    candidates = np.flatnonzero(mask)
    customers = candidates[candidates != 0]
    if method == 'gnn' or len(customers) == 0:
        return mask
    if method == 'gnn_g':
        keep = sorted(customers, key=lambda i: (env.costs[env.position, i], i))[:k]
    elif method == 'gnn_cw':
        keep = savings_candidates(env, k)
    else:
        raise ValueError(method)
    result = np.zeros_like(mask)
    result[keep] = True
    result[0] = mask[0]
    return result


def cw_plan(env):
    """Rebuild directed savings merges on currently unserved customers."""
    nodes = [int(i) for i in env.customers if not env.served[i]]
    routes = [[i] for i in nodes]
    d, c = env.known_demands(), env.costs
    pairs = sorted(((c[i, 0] + c[0, j] - c[i, j], i, j)
                    for i in nodes for j in nodes if i != j), reverse=True)
    for saving, i, j in pairs:
        if saving <= 0:
            continue
        ri = next(r for r in routes if i in r)
        rj = next(r for r in routes if j in r)
        if ri is not rj and ri[-1] == i and rj[0] == j and d[ri+rj].sum() <= env.config.capacity:
            routes.remove(ri)
            routes.remove(rj)
            routes.append(ri+rj)
    return routes


class Baseline:
    def __init__(self, method):
        if method not in BASELINES:
            raise ValueError(method)
        self.method, self.tabu = method, []

    def action(self, env):
        feasible = [int(i) for i in np.flatnonzero(env.mask()) if i != 0]
        if not feasible:
            return 0
        c, d, p = env.costs, env.known_demands(), env.position
        nearest = lambda i: (c[p, i], i)
        if self.method == 'random':
            return int(env.action_rng.choice(feasible))
        if self.method == 'largest_demand':
            return min(feasible, key=lambda i: (-d[i], *nearest(i)))
        if self.method == 'max_reachable':
            return min(feasible, key=lambda i: (
                -sum(d[j] <= env.load-d[i] for j in feasible if j != i), *nearest(i)))
        if self.method == 'sweep':
            delta = env.coords - env.coords[0]
            angles = np.arctan2(delta[:, 1], delta[:, 0])
            return min(feasible, key=lambda i: (angles[i], *nearest(i)))
        if self.method == 'cw':
            routes = cw_plan(env)
            # Prefer the start of a high-savings route; recompute every decision.
            for route in sorted(routes, key=lambda r: (-len(r), c[p, r[0]])):
                if route[0] in feasible:
                    return route[0]
        if self.method == 'tabu':
            choice = min([i for i in feasible if i not in self.tabu] or feasible, key=nearest)
            self.tabu = (self.tabu + [choice])[-20:]
            return choice
        return min(feasible, key=nearest)


def solve(env, method, agent=None, device='cpu', deterministic=False):
    if method in BASELINES:
        solver = Baseline(method)
        while not env.complete and not env.truncated:
            env.step(solver.action(env))
    elif method in LEARNED:
        if agent is None:
            raise ValueError('a trained checkpoint is required')
        import torch
        from .model import tensors
        agent.eval()
        memory = None
        with torch.inference_mode():
            while not env.complete and not env.truncated:
                x, costs, _ = tensors(env, device)
                logits, _, memory = agent(x, costs, memory)
                mask = torch.as_tensor(hybrid_mask(env, method), device=device)
                dist = agent.distribution(logits, mask)
                action = dist.probs.argmax() if deterministic else dist.sample()
                env.step(action.item())
    else:
        raise ValueError(method)
    return env.result()
