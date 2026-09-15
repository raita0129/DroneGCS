# DroneGCS

Android 平板／手機上的**無人機機隊地面控制站（Ground Control Station）**。以 MAVSDK-Java 連線多台 PX4 無人機，提供即時遙測地圖、單機與全機隊的起飛／降落控制，以及以虛擬領隊（virtual leader）為基準的隊形飛行控制。

> ⚠️ **本專案尚未經過實機或 SITL 的完整飛行驗證**，目前僅確認可編譯出 APK。請先閱讀「[已知限制與安全須知](#已知限制與安全須知)」再用於任何真實飛行。

---

## 功能

| 功能 | 說明 |
| --- | --- |
| 多機連線管理 | 以 `droneId` 索引，每台無人機各自啟動一個 `mavsdk_server` 實例；支援 UDP listen／UDP connect／Serial 三種連線模式 |
| 即時遙測地圖 | osmdroid 離線圖磚，每台無人機一個 marker，顯示飛行模式與電量；遙測逾時（>3 秒）自動標示為「資料過期」並半透明化 |
| 飛行控制 | 單機與全機隊的 arm → takeoff → land 流程，由 `MissionStateMachine` 管控狀態轉換合法性 |
| 隊形飛行 | 三角／直線／方形隊形，以 200ms 定頻控制迴圈透過 Offboard 全域座標 setpoint 下達 |
| 防碰撞 | `PotentialFieldAdjuster`（人工勢場連續排斥修正）為主，`CollisionGuard`（最小間距硬門檻）為最後防線 |
| 遙測驗證 | `TelemetryValidator` 過濾 NaN／超出範圍的經緯度、高度值，異常資料不往上層送 |
| 背景常駐 | Foreground Service（`connectedDevice` 類型）維持連線；`START_STICKY`，系統重啟 Service 後自動以上次設定恢復連線 |
| 崩潰處理 | 全域 `CrashHandler` 記錄堆疊並強制 flush log；5 分鐘內崩潰 3 次判定為崩潰迴圈，停止自動重連並要求使用者手動確認 |
| Log 蒐集匯出 | CSV 格式落地、自動輪替（單檔 5MB／保留 10 檔），可一鍵打包成 zip 透過系統分享匯出 |

---

## 架構

嚴格單向的 MVVM 分層：**資料只往上流、操作只往下呼叫**，`DroneViewModel` 以上的層級不出現任何 MAVSDK 類別。

```
MainActivity / DroneStatusAdapter / MapController   ← UI 層
        ↕ LiveData / 方法呼叫
DroneViewModel                                      ← 狀態持有與轉換
        ↕
DroneFleetRepository                                ← Service 綁定的抽象層
        ↕ LocalBinder
GcsControlService（Foreground Service）              ← 生命週期與指令彙整
        ↕
DroneFleetManager ─ SwarmFormationCoordinator       ← 連線管理與控制迴圈
        ↕                ├ FormationController
   MAVSDK / mavsdk_server├ PotentialFieldAdjuster
   CustomMessageChannel  └ CollisionGuard
```

### 原始碼結構

```
app/src/main/java/com/raita/dronegcs/
├── GcsApplication.java          # Logger 初始化、掛載 CrashHandler
├── connection/
│   ├── DroneFleetManager.java   # 多機連線、遙測串流、Offboard 指令
│   ├── HeartbeatWatchdog.java   # 心跳逾時偵測（3 秒）
│   ├── CustomMessageChannel.java# 繞過 MAVSDK 的獨立 UDP 自訂訊息通道
│   └── MavlinkV2Codec.java      # 手刻的 MAVLink v2 封包編解碼（暫時方案，見已知限制）
├── control/
│   ├── DroneFleetRepository.java
│   ├── MissionStateMachine.java # IDLE→CONNECTED→ARMED→FLYING→LANDED
│   ├── SwarmFormationCoordinator.java # 200ms 控制迴圈
│   ├── FormationController.java # 虛擬領隊 + 相對偏移 → 目標經緯度
│   ├── PotentialFieldAdjuster.java    # 人工勢場排斥修正（半徑 3m）
│   └── CollisionGuard.java      # 最小安全間距硬門檻（2m）
├── core/
│   ├── ConnectionConfig.java    # 連線設定與 MAVSDK URL 組裝
│   ├── ConnectionConfigStore.java # JSON 匯入 / SharedPreferences 保存
│   ├── DroneState.java / PositionTarget.java / Vector3.java
│   ├── GeoUtils.java            # NED 偏移量 ↔ 經緯度換算
│   └── TelemetryValidator.java
├── debug/                       # Logger / LogCollector / LogExporter / CrashHandler
├── service/GcsControlService.java
└── ui/                          # MainActivity / DroneViewModel / MapController / DroneStatusAdapter
```

---

## 技術棧

- **語言／建置**：Java 11、Gradle 9.5.0、AGP 9.3.2（Gradle daemon toolchain：JDK 25）
- **Android**：minSdk 24、targetSdk／compileSdk 37
- **飛控通訊**：[MAVSDK-Java](https://github.com/mavlink/MAVSDK-Java) `3.16.0` + `mavsdk-server` `3.16.0`（版本號必須成對，否則 gRPC／proto 定義兜不起來）
- **非同步**：RxJava 2.2.21 + RxAndroid 2.1.1
- **地圖**：osmdroid 6.1.18
- **其他**：Gson、AndroidX Lifecycle／RecyclerView／CardView、Material Components

---

## 建置與執行

### 需求

- Android Studio（支援 AGP 9.x 的版本）
- JDK 11 以上（Gradle daemon 會自動下載 JDK 25 toolchain）
- **arm64-v8a 裝置**：`app/src/main/jniLibs/` 目前只放了 `arm64-v8a/libc++_shared.so`，模擬器（x86_64）無法執行

### 建置

```bash
./gradlew assembleDebug          # 產出 debug APK
./gradlew installDebug           # 安裝到已連線的裝置
./gradlew compileDebugJavaWithJavac  # 只做編譯檢查
```

`local.properties`（Android SDK 路徑）未納入版控，首次由 Android Studio 開啟專案時會自動產生。

---

## 連線設定

App 啟動時會讀取上次保存的設定；也可以按 **匯入設定** 選擇一份 JSON 檔（`application/json`）匯入並立即連線。

```json
[
  { "droneId": "drone1", "mode": "UDP_CONNECT", "host": "192.168.x.x", "port": 18570 },
  { "droneId": "drone2", "mode": "UDP_LISTEN",  "port": 14540 }
]
```

| 欄位 | 說明 |
| --- | --- |
| `droneId` | 機體識別字串，全系統以此為索引，必填且不可重複 |
| `mode` | `UDP_LISTEN`（`udpin://:port`，等待對方連入）／`UDP_CONNECT`（`udpout://host:port`，主動連到已知 IP）／`SERIAL`（`serial://path:baud`） |
| `host` | `UDP_CONNECT` 模式必填 |
| `port` | 1–65535；`SERIAL` 模式此欄位代表 baud rate |
| `serialPath` | `SERIAL` 模式必填，例如 `/dev/ttyUSB0` |
| `companionHost` / `companionPort` | 自訂 UDP 訊息通道對接的機載電腦位址，預設 `127.0.0.1` |

---

## 使用流程

1. 啟動 App → 自動綁定 Foreground Service 並以保存的設定連線
2. 需要改設定時，按 **匯入設定** 選擇 JSON 檔
3. 上方狀態列顯示已連線台數；清單列出每台無人機的模式、電量與獨立的 **起飛／降落** 按鈕
4. **全部起飛 / 全部降落** 對所有已連線機體下達指令
5. **啟動隊形** 會以目前所有機體位置的平均值作為虛擬領隊原點，讓機體進入 Offboard 模式並開始定頻送出 setpoint；**停止隊形** 會退出 Offboard
6. **匯出 Log** 將所有 CSV log 打包成 zip，透過系統分享面板送出

---

## 已知限制與安全須知

- **未經飛行驗證**：MAVSDK 3.16.0 的 API 簽名已逐一對照原始碼確認、也確認可編譯出 APK，但**連線／起飛／隊形等實際行為尚未在實機或 SITL 上跑過**。正式使用前務必先以 SITL 完整驗證一次。
- **App 崩潰不等於無人機失控**：真正的飛安防線是飛控端（PX4）自己的 offboard timeout failsafe。本專案的崩潰恢復機制只負責讓 App 恢復得更妥當，不是無人機安全的第一道防線。
- **自訂訊息 CRC 尚未定案**：`CustomMessageChannel` 的 `CUSTOM_STATUS_CRC_EXTRA` 目前是佔位值 `0`，`MavlinkV2Codec` 是手刻的暫時實作。正確做法是用 `mavgen --lang=Java` 產生官方程式庫，待 SITL 環境重建後再處理。此通道走獨立的 `DatagramSocket`，與 MAVSDK 的連線完全分離。
- **隊形機數上限**：`TRIANGLE` 最多 3 台、`SQUARE` 最多 4 台（超出的機體不會被指派偏移量）；`LINE` 無上限。
- **安全間距為固定值**：`CollisionGuard.SAFE_DISTANCE_METERS = 2.0`、`PotentialFieldAdjuster` 排斥半徑 3.0m，皆以小型機體為前提，實際使用請依機體尺寸調整。
- **無定位權限**：App 不請求裝置定位權限，地圖只顯示無人機位置，不顯示操作者位置。
- **未整合當機回報服務**：刻意不整合 Firebase Crashlytics 等雲端服務，避免外部帳號依賴與資料隱私疑慮；除錯改以本地 log 匯出進行。
