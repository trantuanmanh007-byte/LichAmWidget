# Lịch Âm & Thời Tiết — Android Widget

Widget màn hình chính Android hiển thị đồng hồ tự động (`TextClock`), lời chào theo khung giờ, lịch Dương – Âm Việt Nam (thuật toán Hồ Ngọc Đức UTC+7 offline), năm Can Chi, lịch tuần T2–CN, dự báo thời tiết 5 ngày (Open-Meteo miễn phí không cần API Key) và phần trăm/nhiệt độ pin thực tế.

## Cách tự đóng gói thành file `.apk` bằng GitHub (không cần cài Android Studio)

1. Tạo một tài khoản miễn phí trên [github.com](https://github.com) rồi bấm **New repository** (đặt tên ví dụ `LichAmWidget`, để Public hoặc Private đều được).
2. Giải nén file `LichAmWidget_Android.zip`, tải toàn bộ các thư mục và file bên trong lên kho lưu trữ vừa tạo (chú ý giữ nguyên thư mục ẩn `.github/workflows/build-apk.yml`).
3. Chuyển sang thẻ **Actions** trên GitHub → chọn workflow **Build Android APK** → bấm **Run workflow** (hoặc chờ chạy tự động ngay sau khi push).
4. Sau khoảng 2–3 phút có dấu tích xanh, bấm vào lần chạy đó → cuộn xuống mục **Artifacts** → tải về **LichAmWidget-APK**, giải nén lấy file `app-debug.apk` và cài lên điện thoại Android.

## Cách biên dịch trên máy tính (Android Studio)

1. Mở Android Studio → **Open** → chọn thư mục `LichAmWidget_Android`.
2. Đợi Gradle tự tải bộ công cụ (`Gradle 8.7`).
3. Chọn **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
