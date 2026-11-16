# Đối chiếu báo cáo, mã tác giả và bản tái dựng

Nguồn chính: PDF do người dùng cung cấp, DOI 10.1016/j.orp.2025.100351, mục 3–5 và Bảng 1–10. Nguồn tham khảo triển khai: notebook trong kho tác giả (tài liệu [38] của bài).

Notebook tham chiếu được truy xuất từ kho `shiralikadyrov/CVRP_uncertainty`, nhánh main. Git blob SHA: `6f2e7293811039108151d2ef8f2979309155679c`. Không có pretrained checkpoint trong danh sách file ở gốc kho khi kiểm tra.

## Các phần bám thông số bài báo

- Một xe, nhiều lượt quay về kho, tải mặc định 15.
- Kho tại tâm lưới; khách tại các ô nguyên, không trùng vị trí.
- Chi phí Manhattan, c_ii=0, traffic theo công thức (1), beta=.07, sigma_T=1, m=5.
- Nhu cầu danh nghĩa nguyên 1..5; nhu cầu thực tế Gaussian, làm tròn, chặn [1,C-1] theo công thức (2).
- Hai NNConv, hidden=64; edge MLP 1→32→in×out theo notebook.
- Transformer hidden=64, 4 heads, 2 layers; FFN=128 theo notebook.
- GRU 64; policy phân phối trên hành động được mask; critic MLP 64→64→1.
- Reward = -chi phí tăng thêm +5×số khách mới phục vụ -0,5.
- PPO gamma=.5, clip=.2, 4 epochs/episode, Adam lr=1e-4, grad norm=1, không LR decay.
- GNN-G dùng k=3; GNN-CW dùng savings để giới hạn ứng viên.
- Các kích thước và 7 kịch bản đúng các bảng; mặc định 100 seed/kích thước/kịch bản.

## Các quyết định bắt buộc do thiếu chi tiết hoặc mâu thuẫn

| Vấn đề | Bài báo / notebook | Bản tái dựng |
|---|---|---|
| Số khách huấn luyện | Mục 4.2: 10–63; kết luận: tối đa 50; notebook còn cắt mẫu theo tham số num_customers | Mặc định phân phối đều 10–63, có CLI đổi sang 50; không nhận là phân phối giống notebook |
| Số node trong mạng | Bài mô tả N+1; notebook dùng toàn bộ n² ô, kể cả ô không có khách | Theo layout trạng thái 6 đặc trưng và toàn lưới của notebook; ô không có khách bị mask |
| Tâm kho và khoảng cách | Notebook tráo ID kho nhưng vẫn tính distance theo ID cũ | Hoán đổi tọa độ, tính Manhattan từ tọa độ đã hoán đổi để kho thực sự ở tâm |
| GRU | Notebook cập nhật hidden_state nhưng không dùng đầu ra GRU trong logits; pooled node context cộng chung trước linear head chỉ tạo dịch chuyển chung, bị softmax triệt tiêu | Nối embedding từng nút với hidden GRU, Linear(128,64)→tanh→Linear(64,1). Đây là lựa chọn kiến trúc được công khai, không phải công thức chi tiết có trong PDF |
| Critic | PDF mô tả mạng value 2 lớp; notebook lấy trung bình returns làm baseline, không có mạng value | Dùng value MLP 2 lớp như PDF; hệ số loss value=.5 là lựa chọn bổ sung |
| Attention | PDF gọi NNConv là attention-based nhưng không cho công thức trọng số attention riêng | Dùng mean NNConv như notebook; attention nằm trong Transformer. Không tự thêm GAT |
| Dropout | PDF không nêu; Transformer notebook có dropout mặc định | Dropout=0 để likelihood PPO rollout/replay nhất quán |
| Entropy | PDF không nêu hệ số | .01 theo notebook |
| Thiếu tải khi tiết lộ nhu cầu | PDF đòi khả thi/mỗi khách thăm đúng một lần; notebook giữ khách chưa phục vụ nếu thiếu hàng | Ghi nhận failed service, bắt buộc về kho, retry; có thể thăm một khách nhiều lần nhưng chỉ phục vụ thành công một lần. Không khẳng định đúng ràng buộc “visited exactly once” |
| Mask nhu cầu | Notebook dùng expected ngay cả khi đã tiết lộ nhu cầu | Dùng expected trước khi tiết lộ, actual sau tiết lộ; không lộ giá trị trước chuyến thăm |
| Kho tự lặp | Notebook cho depot luôn khả thi, kể cả đang ở depot | Chặn depot self-loop khi còn khách khả thi, tránh bước không di chuyển vô ích |
| Thời điểm traffic | Notebook step cập nhật trước khi tính chi phí, nhiều baseline còn cập nhật thêm một lần | Thống nhất một cập nhật trước mỗi movement, gồm cả lượt quay về kho; có cập nhật ban đầu như notebook |
| RNG nhu cầu | Notebook lấy mẫu theo thứ tự thăm, không gắn cố định từng khách giữa các phương pháp | Sinh hidden demand cố định cho từng khách theo seed, chỉ tiết lộ lúc đến; traffic/action RNG tách biệt để so sánh ghép cặp |
| GRU/PPO replay | PDF không nêu cách replay chuỗi | Lưu hidden đầu vào từng bước từ rollout; replay có detach hidden, gradient horizon 1. Không phải BPTT toàn episode |
| Giới hạn bước | PDF 5×số node; evaluator notebook dùng nhiều giới hạn khác nhau | Thống nhất 5×n²; mọi lượt chưa xong được đánh dấu truncated và không lấy cost làm lời giải hoàn chỉnh |
| Bootstrap ở giới hạn | PDF không rõ | Xem horizon hữu hạn là terminal, bootstrap=0 |
| Infinite capacity | PDF không nêu encoding feature tải vô hạn | Capacity thực vô hạn; chỉ feature load dùng 15 để tránh NaN/Inf trong mạng; nhu cầu thực không chặn trên bởi 14 ở kịch bản này |
| Kết thúc tuyến | Một số hàm notebook cộng chi phí về kho trực tiếp, không gọi env.step | Mọi chuyến quay về kho đều qua cùng step, không cộng chặng ảo ngoài simulator |

## Biến thể lai và baseline

`gnn_cw` tái tính savings ở mỗi quyết định, chọn tối đa 3 ứng viên khả thi từ các cặp savings cao, fallback sang gần nhất; lấy mẫu theo policy trên tập này (và kho nếu khả thi). Công thức savings c(0,i)+c(0,j)-c(i,j) theo notebook. Notebook gốc lọc một danh sách savings ban đầu theo thời gian, rồi chọn ứng viên gần nhất gần như tất định; không có bằng chứng GRU hay probabilities quyết định lựa chọn ở nhánh thông thường. Vì vậy `gnn_cw` ở đây bám ý nghĩa “savings-guided policy” của bài và **không chạy y hệt hàm evaluator gốc**.

`cw` tái xây tuyến bằng directed savings merges và tải đã biết ở mỗi quyết định; ưu tiên đầu tuyến ghép dài, sau đó khoảng cách. Đây là cách cụ thể hóa “re-optimized dynamically” trong bài; không phải CW evaluator gốc vốn dựng phần lớn tuyến từ đầu. Sweep chọn thứ tự góc giữa các khách khả thi. Max-Reachable đếm số khách có nhu cầu đã biết còn vừa tải sau khi phục vụ ứng viên; dùng khoảng cách phá hòa. Những quy tắc phá hòa là lựa chọn bổ sung.

`tabu` bám baseline “nearest unvisited with tabu tenure 20” được bài mô tả. Vì khách đã phục vụ bị loại, tabu thường gần tương đương Greedy; không được gọi đây là một triển khai Tabu Search mạnh với neighborhood optimization.

Mọi baseline dùng cùng quy tắc mask/recourse/horizon để so sánh công bằng, khác notebook có các quy tắc không thống nhất. Vì thế không dùng số liệu sinh từ bản này để khẳng định đã tái lập nguyên bảng bài báo.

## Bộ nhớ và chi phí tính toán

Với lưới 15×15 có 225 node, complete graph có 50.400 cạnh có hướng. NNConv thứ hai tạo ma trận 64×64 cho từng cạnh; materialize toàn bộ cần khoảng 826 MB float32 chỉ cho edge weights, chưa tính autograd.

Bản dựng tính NNConv theo chunk 256 cạnh, vẫn giữ đúng mean aggregation trên mọi cạnh; activation checkpointing trong training giảm lưu intermediates. Có kiểm thử so output và gradient với PyG NNConv chuẩn. Transformer vẫn có độ phức tạp bậc hai theo số node. Tối ưu bộ nhớ không có nghĩa tốc độ runtime sẽ giống bài báo.

## Tái lập được gì, chưa tái lập được gì

Đã cung cấp chương trình chạy và kiểm thử tính đúng của môi trường, khả thi hành động, hạch toán chi phí, phép tính NNConv, gradient recurrent actor/critic, checkpoint và thống kê.

Chưa xác minh lợi thế chất lượng so với baseline sau huấn luyện dài; chưa xác minh độ nhạy qua nhiều training seeds; chưa có model weights hay test instances của tác giả; chưa thể khẳng định tái lập chính xác cost/runtime/p-value Bảng 3–10. Mọi kết quả từ smoke checkpoint phải ghi nhãn “kiểm tra vận hành”, không dùng làm kết quả nghiên cứu.
