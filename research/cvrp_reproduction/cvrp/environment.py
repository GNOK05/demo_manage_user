"""Single-vehicle stochastic CVRP. No hidden demands are exposed to the policy."""
from dataclasses import asdict, dataclass, replace
import math
import numpy as np


@dataclass(frozen=True)
class EnvConfig:
    grid_size: int = 8
    customers: int = 20
    capacity: float = 15
    beta: float = 0.07
    demand_std: float = 1.0
    traffic_std: float = 1.0
    traffic_offset: float = 5.0
    max_steps_factor: int = 5

    def __post_init__(self):
        if self.grid_size < 2 or not 1 <= self.customers < self.grid_size**2:
            raise ValueError('customers must be in [1, grid_size**2 - 1]')
        if self.capacity < 6 or math.isnan(self.capacity):
            raise ValueError('capacity must be >= 6 (nominal demands are 1..5)')
        if not 0 <= self.beta < 1 or min(self.demand_std, self.traffic_std) < 0:
            raise ValueError('invalid stochastic process parameters')
        if self.max_steps_factor < 1:
            raise ValueError('max_steps_factor must be positive')


SCENARIOS = {
    'standard': {},
    'demand_only': {'traffic_std': 0., 'traffic_offset': 0.},
    'traffic_only': {'demand_std': 0.},
    'infinite_capacity': {'capacity': float('inf')},
    'high_traffic': {'traffic_std': 10.},
    'high_demand': {'demand_std': 3.},
    'no_uncertainty': {'demand_std': 0., 'traffic_std': 0., 'traffic_offset': 0.},
}


def scenario_config(name, **kwargs):
    return replace(EnvConfig(**kwargs), **SCENARIOS[name])


class CVRPEnv:
    """Grid nodes include inactive locations, matching the author's state layout.

    Failed service consumes travel cost but no load: reveal demand, return to
    depot, retry later. This is the author's recourse, not a once-only visit
    guarantee. Traffic updates before each movement as in the notebook.
    """

    def __init__(self, config=EnvConfig(), seed=0):
        self.config = config
        self.reset(seed)

    def reset(self, seed=0):
        self.seed = int(seed)
        streams = np.random.SeedSequence(self.seed).spawn(4)
        self.layout_rng, self.demand_rng, self.traffic_rng, self.action_rng = (
            np.random.default_rng(s) for s in streams
        )
        c = self.config
        self.num_nodes = c.grid_size**2
        self.coords = np.array([(i % c.grid_size, i // c.grid_size)
                                for i in range(self.num_nodes)])
        center = (c.grid_size // 2) * c.grid_size + c.grid_size // 2
        self.coords[[0, center]] = self.coords[[center, 0]]
        self.customers = np.sort(self.layout_rng.choice(
            np.arange(1, self.num_nodes), c.customers, replace=False))
        self.expected = np.zeros(self.num_nodes)
        self.expected[self.customers] = self.layout_rng.integers(1, 6, c.customers)
        # Fixed per-customer hidden draws avoid coupling demands to visit order.
        # Each algorithm receives exactly the same instance for a given seed.
        self._true = np.zeros(self.num_nodes)
        draws = np.rint(self.demand_rng.normal(self.expected[self.customers], c.demand_std))
        self._true[self.customers] = np.clip(draws, 1, c.capacity - 1)
        self.revealed = np.full(self.num_nodes, np.nan)
        self.served = np.zeros(self.num_nodes, dtype=bool)
        self.base = np.abs(self.coords[:, None] - self.coords[None, :]).sum(-1).astype(float)
        self.costs = self.base.copy()
        self.position, self.load = 0, c.capacity
        self.total_cost, self.steps, self.failures = 0., 0, 0
        self.force_depot = False
        self.route, self.step_costs = [0], []
        self._update_traffic()
        return self.observation()

    @property
    def complete(self):
        return bool(self.served[self.customers].all() and self.position == 0)

    @property
    def truncated(self):
        return self.steps >= self.config.max_steps_factor * self.num_nodes and not self.complete

    def _update_traffic(self):
        c = self.config
        noise = self.traffic_rng.normal(0, c.traffic_std, self.costs.shape)
        self.costs = np.maximum(self.base, c.beta * self.costs +
                                (1-c.beta) * self.base + noise + c.traffic_offset)
        np.fill_diagonal(self.costs, 0.)

    def known_demands(self):
        return np.where(np.isnan(self.revealed), self.expected, self.revealed)

    def mask(self):
        mask = np.zeros(self.num_nodes, dtype=bool)
        if not self.force_depot:
            d = self.known_demands()
            mask[self.customers] = (~self.served[self.customers] &
                                     (d[self.customers] <= self.load))
            mask[self.position] = False
        # A depot self-loop is not useful; permit only as terminal fallback.
        mask[0] = self.position != 0 or not mask.any()
        return mask

    def observation(self):
        # Finite feature for infinite capacity; all instances then share the
        # same encoding scale. Actual simulator capacity remains infinite.
        encoded_load = self.load if math.isfinite(self.load) else 15.
        return np.column_stack((self.known_demands(), self.expected,
                                self.served.astype(float),
                                np.arange(self.num_nodes) == self.position,
                                np.full(self.num_nodes, encoded_load),
                                self.costs[self.position])).astype(np.float32)

    def step(self, action):
        action = int(action)
        if self.complete or self.truncated:
            raise RuntimeError('episode already ended')
        if not 0 <= action < self.num_nodes or not self.mask()[action]:
            raise ValueError(f'invalid action {action}')
        self._update_traffic()
        cost = float(self.costs[self.position, action])
        self.total_cost += cost
        self.position = action
        self.steps += 1
        self.route.append(action)
        self.step_costs.append(cost)
        delivered = 0
        if action == 0:
            self.load = self.config.capacity
            self.force_depot = False
        else:
            self.revealed[action] = self._true[action]
            if self.revealed[action] <= self.load:
                self.load -= self.revealed[action]
                self.served[action] = True
                delivered = 1
            else:
                self.failures += 1
                self.force_depot = True
        reward = -cost + 5 * delivered - .5
        return self.observation(), reward, self.complete, self.truncated

    def result(self):
        return dict(cost=self.total_cost, served=int(self.served.sum()),
                    customers=self.config.customers, complete=self.complete,
                    truncated=self.truncated, steps=self.steps, failures=self.failures,
                    route=self.route.copy(), step_costs=self.step_costs.copy())
