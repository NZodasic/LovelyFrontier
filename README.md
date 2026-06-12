# LovelyFrontier ⚔️ - Advanced Instance-based Dungeon Plugin
> Hệ thống quản lý phụ bản độc lập nâng cao dành cho máy chủ Minecraft Paper 1.21.x.
> A production-ready instance-based dungeon plugin for Minecraft Paper 1.21.x.

---

## 🇻🇳 TIẾNG VIỆT (VIETNAMESE)

LovelyFrontier là giải pháp phụ bản độc lập tự động chuyên nghiệp dành cho hệ máy chủ Minecraft (Paper/Purpur 1.21.x). Tiện ích này cung cấp giải pháp tối ưu cho việc khởi tạo các thế giới phụ bản độc lập (instances) bằng cách sao chép thế giới bất đồng bộ (async), quản lý trạng thái phụ bản nghiêm ngặt, tích hợp hệ thống bỏ phiếu độ khó nâng cao và chống trục lợi tuyệt đối.

### 🌟 Tính năng Nổi bật

1. **Vòng đời Phụ bản Nghiêm ngặt (State Machine)**
   * Chuyển trạng thái phụ bản an toàn và nguyên tử (atomic): `LOADING` ➔ `ACTIVE` ➔ `COMPLETING` ➔ `CLEANUP`.
   * Tự động quét và dọn dẹp các thế giới phụ bản lỗi (zombie instances) hoặc cổng hết hạn khi khởi động và tắt máy chủ.

2. **Quy trình Khởi tạo Phụ bản Bất đồng bộ (Saga Pattern)**
   * Khởi tạo phụ bản qua 7 bước an toàn và không gây lag máy chủ (non-blocking).
   * **Cơ chế hoàn trả (Compensation)**: Nếu bất kỳ bước nào trong Saga thất bại (như WorldEdit dán schematic lỗi, lỗi hệ thống), plugin sẽ tự động hoàn tác toàn bộ các thao tác trước đó và trả lại vé cho người chơi.

3. **Cổng Phụ bản Ngẫu nhiên (Dynamic World Spawns)**
   * Tự động tạo ngẫu nhiên các cổng phụ bản trong thế giới thực theo khoảng thời gian định sẵn.
   * Xây dựng cấu trúc Đèn hiệu (Beacon) thực tế (đế Sắt 3x3, Khối Đèn hiệu và Kính màu theo cấp độ phụ bản).
   * Thông báo tọa độ cổng bất đồng bộ cho người chơi qua chế độ thông báo `GLOBAL` (toàn máy chủ), `LOCAL` (trong phạm vi lân cận) hoặc `BOTH` (cả hai).
   * Cổng sẽ tự động despawn dọn dẹp khối khi hết hạn hoặc khi có một nhóm bắt đầu đi vào thành công.

4. **Bảo mật Concurrency Hai lớp (Atomic Portal Lock)**
   * Lock cổng đồng thời trên cả bộ nhớ đệm (In-Memory) và Cơ sở dữ liệu (Database) để ngăn chặn hành vi nhấp đúp hoặc khai thác lag tạo nhiều phụ bản cùng lúc.

5. **Chống Trục lợi & Clone (Anti-Abuse)**
   * **Playtime Gate**: Yêu cầu số giờ chơi tối thiểu để mua vé, nhận quà tuần và phần thưởng phụ bản.
   * **Alt Account Detection**: Mã hóa địa chỉ IP dạng SHA-256 để phát hiện tài khoản phụ (clone) sử dụng chung IP.
   * Giới hạn và giãn cách thời gian sử dụng Cuộn Giấy Cổng dịch chuyển.

---

### 🔌 Danh sách Plugin Bắt buộc (Required Dependencies)

Để LovelyFrontier hoạt động ổn định và chính xác, máy chủ của bạn **BẮT BUỘC** phải cài đặt các plugin sau:

1. **Paper 1.21.x** hoặc **Purpur 1.21.x** (Bản dựng Java 21+)
2. **Multiverse-Core (Mới nhất)**: Dùng để quản lý vòng đời tạo, tải và xóa các thế giới phụ bản tạm thời.
3. **WorldEdit (Mới nhất)**: Thực hiện dán bản vẽ schematic của phụ bản. Thao tác đọc file bản vẽ được thực hiện bất đồng bộ và dán đồng bộ trên luồng chính để đảm bảo an toàn.
4. **MMOCore (Mới nhất)**: Cung cấp API quản lý tổ đội (Party API) để theo dõi nhóm trưởng, các thành viên và trạng thái kết nối.
5. **MythicLib (Mới nhất)**: Thư viện nền tảng bắt buộc để chạy MMOCore.
6. **Vault (1.7+)**: Giao diện kết nối nền kinh tế cho cửa hàng mua vé phụ bản và trao thưởng.
7. **Một Plugin Kinh tế tương thích** (EssentialsX, CMI, Treasury hoặc các plugin kinh tế khác kết nối qua Vault).

---

### 💻 Lệnh & Quyền hạn (Commands & Permissions)

#### Quyền hạn Người chơi (Player Permissions)
* `lf.use` (Mặc định: true): Truy cập các lệnh cơ bản: `/lf`, `/lf tickets`, `/lf leave`, `/lf ready`, `/lf vote`, `/lf top`, `/lf mail`.
* `lf.shop` (Mặc định: true): Cho phép dùng lệnh `/lf shop` để mua vé.
* `lf.queue` (Mặc định: true): Tham gia/rời hàng chờ ghép trận phụ bản.
* `lf.gift` (Mặc định: true): Tặng vé phụ bản cho người chơi khác `/lf gift`.
* `lf.scroll.use` (Mặc định: true): Sử dụng Cuộn Giấy Cổng dịch chuyển cá nhân.
* `lf.scroll.craft` (Mặc định: true): Chế tạo Cuộn Giấy Cổng.

#### Quyền hạn Quản trị viên (Admin Permissions)
* `lf.admin` (Mặc định: op): Toàn quyền truy cập tất cả lệnh hệ thống `/lfa`.
* `lf.admin.reload` (Mặc định: op): Tải lại cấu hình qua `/lfa reload`.
* `lf.admin.instances` (Mặc định: op): Xem danh sách phụ bản đang hoạt động `/lfa instances`.
* `lf.admin.forceclose` (Mặc định: op): Ép đóng phụ bản bằng `/lfa forceclose <id>`.
* `lf.admin.give` (Mặc định: op): Phát vé phụ bản cho người chơi qua `/lfa give`.
* `lf.admin.setspawn` (Mặc định: op): Thiết lập điểm spawn trong phụ bản `/lfa setspawn`.

---

## 🇺🇸 ENGLISH (GENERAL OVERVIEW)

LovelyFrontier is a premium, production-grade dungeon management plugin for Paper/Spigot 1.21.x servers, engineered with concurrency safety, transaction rollbacks, and anti-abuse filters.

### 🌟 Core Systems

* **Saga Orchestration Pattern**: Guarantees atomic instance startup. In case of schematic paste errors or database hiccups, it executes a LIFO compensation sequence to refund tickets and clean up world directories.
* **Dynamic World Spawns**: Spawns random portals across the world configured via timers. Generates real physical Beacon towers in the world (Iron Blocks, Beacon, color-coded Glass) and safely alerts online players.
* **Double-Layer Concurrency Locking**: Prevents double-click exploitation of portals using memory-mapped locks combined with pessimistic database transactions.
* **Anti-Abuse Engine**: Implements playtime gates, limits concurrent IP connections using secure SHA-256 hashes, and limits portal scroll cooldowns.

---

### 🔌 System Prerequisites & Dependencies

Before starting the server, ensure that the following plugins are installed:

1. **Paper 1.21.x** or **Purpur 1.21.x** (Mandatory Java 21+ environment).
2. **Multiverse-Core**: Required for dynamic loading, unloading, and deletion of temporary dungeon world directories.
3. **WorldEdit**: Handles schematic pasting of dungeon configurations (schematic file I/O is handled off-thread and pasted on the main thread for thread-safety).
4. **MMOCore**: Serves as the core party authority to retrieve party leaders, size, and active players.
5. **MythicLib**: Underlying prerequisite engine required by MMOCore.
6. **Vault**: Handles payment and reward gateway operations.
7. **Economy Provider**: (EssentialsX, CMI, etc.) providing currency storage through Vault.

---

### 🛠️ Build & Installation Guide

Compile a shaded, production-ready jar with the local Gradle distribution:

```bash
# Verify execute permissions
chmod +x .gradle-dist/gradle-8.10.2/bin/gradle

# Compile and package shadowed fat JAR
./.gradle-dist/gradle-8.10.2/bin/gradle shadowJar
```

Copy the compiled jar from `build/libs/LovelyFrontier-1.0-SNAPSHOT.jar` directly into your server's `plugins/` folder and restart your server.