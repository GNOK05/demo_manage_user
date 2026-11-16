# Biên bản kiểm tra triển khai

Ngày: 15/11/2026. Nền tảng: Windows, Python 3.12, PyTorch 2.6.0+cpu, PyTorch Geometric 2.6.1. Đây là kiểm tra vận hành và logic, không phải tái lập kết quả khoa học của bài báo.

## Kiểm thử tự động

Lệnh: `.venv/Scripts/python.exe -m pytest -q`

Kết quả: **69 passed**.

Bao gồm: tọa độ kho và khoảng cách; seeded instances; không rò rỉ nhu cầu thật; thiếu tải/quay kho/phục vụ lại; công thức traffic; truncation; 7 baseline × 7 kịch bản; mask 3 chính sách; NNConv chia chunk so với PyG chuẩn cả đầu ra lẫn gradient; GRU ảnh hưởng tới phân phối hành động và nhận gradient; discounted returns; actor/critic cập nhật sau PPO; checkpoint roundtrip; 3 learned rollouts; thống kê loại lượt không hoàn thành; forward trên lưới 8/12/15 với 50/100/200 khách.

## Chạy qua CLI

1. Huấn luyện 2 episode trên lưới 8×8 với 10 khách/episode, seed 42, CPU, 4 PPO epochs mặc định. Cả hai episode phục vụ 10/10 khách và kết thúc ở kho. Lưu `runs/smoke/checkpoint.pt`.
2. Nạp checkpoint qua CLI đánh giá đủ 10 phương pháp, 2 mẫu chuẩn với 20 khách. Xuất CSV, JSON routes và Friedman.
3. Resume checkpoint từ episode 2 lên episode 3; episode 3 phục vụ 10/10 khách, checkpoint lưu lại thành công.
4. Đánh giá lại checkpoint episode 3 qua CLI, đủ 10 phương pháp với 2 mẫu chuẩn; settings ghi episode và SHA-256 để tránh nhầm checkpoint bị ghi đè.
5. Chạy 7 baseline × 7 kịch bản × 3 seed với 20 khách: **147/147 lượt hoàn thành**. Đầu ra `runs/baseline_smoke/`.

Checkpoint và kết quả thử được giữ cục bộ trong `runs/`, không đưa vào Git. Không dùng chúng làm chứng cứ mô hình vượt baseline: 3 episode quá ít để huấn luyện mô hình nghiên cứu.

## Chưa thực hiện

- Huấn luyện 200.000 episode hoặc hơn 100 giờ như mô tả bài.
- Đánh giá đầy đủ 100 mẫu × 4 kích thước × 7 kịch bản × 10 phương pháp.
- Kiểm tra CUDA/Tesla T4 hoặc khớp runtime với Bảng 3.
- Tái lập các mean cost, sai số chuẩn và p-value Bảng 4–10.
- Kiểm định trên dữ liệu giao thông hoặc đơn hàng thực.
