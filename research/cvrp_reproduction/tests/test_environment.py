from dataclasses import replace
import numpy as np
import pytest
from cvrp.environment import CVRPEnv, EnvConfig, SCENARIOS, scenario_config
from cvrp.solvers import BASELINES, hybrid_mask, solve


def test_center_and_distances():
    env = CVRPEnv()
    assert env.coords[0].tolist() == [4, 4]
    assert np.all(env.base == abs(env.coords[:, None]-env.coords[None, :]).sum(-1))
    assert len(set(env.customers)) == 20 and 0 not in env.customers


def test_reproducibility_and_no_hidden_demand_leak():
    a, b = CVRPEnv(seed=13), CVRPEnv(seed=13)
    np.testing.assert_array_equal(a.costs, b.costs)
    np.testing.assert_array_equal(a._true, b._true)
    before = a.observation().copy()
    a._true[a.customers] = 14
    np.testing.assert_array_equal(before, a.observation())


def test_failed_service_then_refill_retry():
    env = CVRPEnv(EnvConfig(customers=1, demand_std=0, traffic_std=0))
    node = int(env.customers[0])
    env.expected[node], env._true[node], env.load = 1, 8, 2
    _, reward, done, _ = env.step(node)
    assert not done and not env.served[node] and env.load == 2
    assert env.failures == 1 and env.revealed[node] == 8
    assert np.flatnonzero(env.mask()).tolist() == [0]
    assert reward == -env.step_costs[-1]-.5
    env.step(0)
    env.step(node)
    env.step(0)
    assert env.complete and env.load == 15
    assert sum(env.step_costs) == env.total_cost


def test_exact_traffic_equation():
    env = CVRPEnv(EnvConfig(traffic_std=0))
    previous = env.costs.copy()
    env._update_traffic()
    expected = np.maximum(env.base, .07*previous+.93*env.base+5)
    np.fill_diagonal(expected, 0)
    np.testing.assert_allclose(env.costs, expected)


def test_truncation_not_mistaken_for_completion():
    env = CVRPEnv(EnvConfig(customers=2, max_steps_factor=1))
    env.steps = env.num_nodes
    assert env.truncated and not env.complete
    with pytest.raises(RuntimeError):
        env.step(0)


@pytest.mark.parametrize('scenario', SCENARIOS)
@pytest.mark.parametrize('method', BASELINES)
def test_baselines_finish_all_scenarios(scenario, method):
    env = CVRPEnv(scenario_config(scenario), seed=9)
    result = solve(env, method)
    assert result['complete'] and result['served'] == env.config.customers
    assert env.load >= 0 and env.route[-1] == 0
    assert np.isfinite(env.observation()).all()
    assert result['cost'] == pytest.approx(sum(result['step_costs']))


@pytest.mark.parametrize('method', ['gnn', 'gnn_g', 'gnn_cw'])
def test_hybrid_masks_preserve_feasibility(method):
    env = CVRPEnv()
    mask = hybrid_mask(env, method)
    assert mask.any() and not (mask & ~env.mask()).any()
    if method != 'gnn':
        assert mask[1:].sum() <= 3


def test_invalid_action_rejected():
    env = CVRPEnv()
    inactive = next(i for i in range(1, env.num_nodes) if i not in env.customers)
    with pytest.raises(ValueError):
        env.step(inactive)
