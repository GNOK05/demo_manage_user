# Tái dựng GNN–DRL cho CVRP với bất định nhu cầu và giao thông

Triển khai nghiên cứu độc lập theo **Kadyrov et al. (2025), “Deep reinforcement learning for dynamic vehicle routing with demand and traffic uncertainty”**, Operations Research Perspectives 15, 100351. DOI: https://doi.org/10.1016/j.orp.2025.100351.

**Trạng thái:** có môi trường, mạng, PPO, huấn luyện/lưu/tiếp tục checkpoint, 3 biến thể suy luận, 7 baseline, 7 kịch bản, xuất kết quả và Friedman test. Chưa huấn luyện 200.000 episode, chưa tái lập các con số Bảng 3–10. Checkpoint trong `runs/smoke` chỉ dùng kiểm tra vận hành.

Đây là bản dựng theo mô tả bài báo, **không phải bản sao chạy giống hệt notebook tác giả**. Những chỗ báo cáo thiếu thông tin hoặc khác mã tác giả được liệt kê trong [REPRODUCTION_NOTES.md](REPRODUCTION_NOTES.md). Không thể tuyên bố tái lập chính xác số liệu khi chưa có checkpoint, dữ liệu thử và random seeds gốc.

## Cài đặt và kiểm tra trên Windows

Chạy PowerShell tại thư mục này. Python 3.11 được bài báo sử dụng; bản này cũng đã kiểm tra trên Python 3.12 CPU.

```powershell
python -m venv .venv
.venv/Scripts/python.exe -m pip install torch==2.6.0 --index-url https://download.pytorch.org/whl/cpu
.venv/Scripts/python.exe -m pip install -r requirements.txt
.venv/Scripts/python.exe -m pytest -q
```

Với máy NVIDIA dùng CUDA 12.4, thay bước cài Torch bằng:

```powershell
.venv/Scripts/python.exe -m pip install torch==2.6.0 --index-url https://download.pytorch.org/whl/cu124
```

PyTorch Geometric ở đây dùng NNConv và phép tổng hợp Torch thuần, không cần cài thêm torch-scatter. Thư mục `.venv` và `runs` được bỏ qua trong Git.

## Chạy thử toàn bộ luồng

```powershell
.venv/Scripts/python.exe -m cvrp.train --episodes 2 --min-customers 10 --max-customers 10 --output runs/smoke --save-every 1
.venv/Scripts/python.exe -m cvrp.evaluate --checkpoint runs/smoke/checkpoint.pt --instances 3 --sizes 20 --scenarios standard --methods gnn gnn_g gnn_cw tabu greedy random largest_demand max_reachable cw sweep --output runs/smoke_evaluation
```

Hai episode đủ kiểm tra forward/backward, lưu/nạp checkpoint và định dạng kết quả; không đủ để đánh giá chất lượng giải thuật. Mạng chưa học tốt có thể không hoàn thành trước giới hạn bước. Bộ đánh giá luôn báo `completion_rate`, không lấy chi phí của lời giải thiếu khách làm thành tích.

## Huấn luyện theo thông số báo cáo

```powershell
.venv/Scripts/python.exe -m cvrp.train --episodes 200000 --device cuda --output runs/paper
```

Mặc định: lưới 8×8, mỗi episode lấy ngẫu nhiên đều 10–63 khách, tải 15, beta 0,07, sigma_D=sigma_T=1, m=5; 2 NNConv, hidden 64, Transformer 4 heads/2 layers, GRU 64, value MLP 2 lớp; gamma 0,5, PPO clip 0,2, 4 epochs, Adam 1e-4, gradient clipping 1. Mỗi episode tối đa 5×64 bước.

Bài báo mâu thuẫn về cận trên khách huấn luyện: mục 4.2 ghi 63 nhưng kết luận ghi 50. Mặc định bám mục 4.2; dùng `--max-customers 50` để chạy theo kết luận. Việc này là một thí nghiệm riêng, không được trộn kết quả.

Nếu không có GPU, bỏ `--device cuda`; chương trình tự dùng CPU. Huấn luyện đầy đủ là tác vụ dài và chưa được khởi chạy trong lần triển khai này.

Tiếp tục huấn luyện (giữ cùng seed và khoảng khách):

```powershell
.venv/Scripts/python.exe -m cvrp.train --resume runs/paper/checkpoint.pt --episodes 200000 --device cuda --output runs/paper
```

Checkpoint gồm trọng số actor/critic, optimizer, episode, cấu hình và RNG Torch. Ghi file tạm rồi thay thế checkpoint sau khi ghi xong. `training.jsonl` chứa reward, loss, cost, số khách đã phục vụ, số lần thiếu hàng và tuyến từng episode.

## Đánh giá bộ thí nghiệm đầy đủ

```powershell
.venv/Scripts/python.exe -m cvrp.evaluate --checkpoint runs/paper/checkpoint.pt --device cuda --instances 100 --methods gnn gnn_g gnn_cw tabu greedy random largest_demand max_reachable cw sweep --output runs/full_evaluation
```

Mặc định chạy các cặp (N,n): (20,8), (50,8), (100,12), (200,15), với 7 kịch bản:

| Tên | Thay đổi so với chuẩn |
|---|---|
| standard | Không thay đổi |
| demand_only | sigma_T=0, m=0 |
| traffic_only | sigma_D=0 |
| infinite_capacity | C=vô hạn |
| high_traffic | sigma_T=10 |
| high_demand | sigma_D=3 |
| no_uncertainty | sigma_D=sigma_T=m=0 |

Mỗi phương pháp nhận cùng vị trí, nhu cầu thật và chuỗi traffic theo seed. Nhu cầu thật được giữ kín đến lần thăm đầu tiên. Chính sách có RNG riêng; việc chọn hành động không làm thay đổi RNG traffic. Mặc định suy luận lấy mẫu xác suất theo mô tả; `--deterministic` chuyển sang argmax và phải xem là thiết lập khác.

Đầu ra:

- `instances.csv`: chi phí, thời gian, trạng thái hoàn thành, số bước, số lần thiếu hàng từng mẫu/phương pháp.
- `summary.csv`: mean cost ± standard error **chỉ trên các lượt hoàn thành**, completion rate, mean runtime, mean failures và mean z-score trên các block hoàn thành đồng thời.
- `friedman.json`: omnibus test riêng mỗi kịch bản/kích thước trên các seed mà mọi phương pháp đều hoàn thành. Không suy ra thắng từng cặp từ p-value này.
- `routes.jsonl`: tuyến, chi phí từng chặng, tọa độ và tập khách; đủ để kiểm tra lời giải hoặc vẽ lại.
- `settings.json`: tham số đánh giá và checkpoint sử dụng.

Runtime đo từ lúc bắt đầu giải sau khi đã tạo instance; gồm tạo đặc trưng, cập nhật môi trường và suy luận. CUDA được đồng bộ trước/sau phép đo. Không đồng nhất với thời gian trong bài vì khác máy, implementation và chưa có quy trình timing gốc. Không dùng thời gian này để tuyên bố tái lập Bảng 3.

## Cấu trúc mã

| File | Nội dung |
|---|---|
| `cvrp/environment.py` | Sinh instance, quan sát, ràng buộc, chuyển trạng thái, 7 kịch bản |
| `cvrp/model.py` | 2 NNConv, Transformer, GRU, policy và value |
| `cvrp/train.py` | PPO episode-wise, log, checkpoint, resume |
| `cvrp/solvers.py` | 7 baseline và 2 hybrid masks |
| `cvrp/evaluate.py` | Đánh giá ghép cặp, CSV/JSON, Friedman |
| `tests/` | Kiểm tra môi trường, NNConv output/gradient, recurrent policy, PPO và thống kê |
| `reference/author_notebook.ipynb` | Notebook tham chiếu từ kho do bài báo dẫn, không tự động thực thi |

Notebook tham chiếu: https://github.com/shiralikadyrov/CVRP_uncertainty/blob/main/CVRP_Traffic_and_Demand_Uncertainty_github.ipynb. Bản tham chiếu được lưu để đối chiếu, giữ nguyên nội dung; quyền sử dụng mã tác giả tuân theo chủ sở hữu, không tự gán giấy phép mới.
