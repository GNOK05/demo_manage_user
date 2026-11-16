"""NNConv(6,64) -> NNConv(64,64) -> Transformer -> GRU actor/critic."""
import torch
from torch import nn
from torch.distributions import Categorical
from torch_geometric.nn import NNConv


class RoutingAgent(nn.Module):
    def __init__(self, hidden=64, edge_chunk=256):
        super().__init__()
        self.hidden = hidden
        self.edge_chunk = edge_chunk
        self.conv1 = NNConv(6, hidden, nn.Sequential(
            nn.Linear(1, 32), nn.ReLU(), nn.Linear(32, 6*hidden)), aggr='mean')
        self.conv2 = NNConv(hidden, hidden, nn.Sequential(
            nn.Linear(1, 32), nn.ReLU(), nn.Linear(32, hidden*hidden)), aggr='mean')
        self.transformer = nn.TransformerEncoder(nn.TransformerEncoderLayer(
            hidden, 4, dim_feedforward=2*hidden, dropout=0., batch_first=True), 2)
        self.gru = nn.GRU(hidden, hidden, batch_first=True)
        # Paper does not specify how node embeddings and memory are fused.
        # Nonlinear fusion is necessary: a shared scalar shift cancels in softmax.
        self.fusion = nn.Linear(2*hidden, hidden)
        self.policy = nn.Linear(hidden, 1)
        self.value = nn.Sequential(nn.Linear(hidden, hidden), nn.ReLU(), nn.Linear(hidden, 1))

    def _conv(self, layer, x, costs):
        """Exact complete-graph NNConv mean, chunked to avoid E*64*64 memory.

        Chunks are checkpointed during training; all non-self directed edges
        remain present. This changes memory use, not the aggregation formula.
        """
        from torch.utils.checkpoint import checkpoint
        n = x.shape[0]
        src, dst = torch.where(~torch.eye(n, dtype=torch.bool, device=x.device))
        out = x.new_zeros(n, layer.out_channels)

        def messages(features, attrs):
            weights = layer.nn(attrs).view(-1, layer.in_channels_l, layer.out_channels)
            return torch.bmm(features.unsqueeze(1), weights).squeeze(1)

        for start in range(0, src.numel(), self.edge_chunk):
            s, d = src[start:start+self.edge_chunk], dst[start:start+self.edge_chunk]
            features, attrs = x[s], costs[s, d, None]
            msg = checkpoint(messages, features, attrs, use_reentrant=False) if self.training else messages(features, attrs)
            out = out.index_add(0, d, msg)
        out = out / (n-1)
        return out + layer.lin(x) + layer.bias

    def forward(self, features, costs, memory=None):
        x = torch.relu(self._conv(self.conv1, features, costs))
        x = torch.relu(self._conv(self.conv2, x, costs))
        x = self.transformer(x.unsqueeze(0)).squeeze(0)
        pooled = x.mean(0).view(1, 1, -1)
        _, memory = self.gru(pooled, memory)
        context = memory[-1, 0]
        fused = torch.tanh(self.fusion(torch.cat((x, context.expand_as(x)), -1)))
        return self.policy(fused).squeeze(-1), self.value(context).squeeze(), memory

    @staticmethod
    def distribution(logits, mask):
        if not bool(mask.any()):
            raise ValueError('no feasible action')
        return Categorical(logits=logits.masked_fill(~mask, -torch.inf))


def tensors(env, device):
    return (torch.as_tensor(env.observation(), device=device),
            torch.as_tensor(env.costs, dtype=torch.float32, device=device),
            torch.as_tensor(env.mask(), device=device))
