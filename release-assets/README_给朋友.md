# Syx Duo Realm V71 朋友测试包

这是 Songs of Syx V71 的双人异步宏观联机 Mod 测试包，版本 0.3.1。它不是实时同城联机，城市内部仍然各自单机模拟。当前第一版目标是：

- 自动导出并上传本地城市状态
- 查看房间里双方是否在线且状态新鲜
- 发送和领取异步贸易包
- 在游戏内切换发送贸易的资源和数量
- 创建一个好友影子 NPC 势力，并把好友人口/资源投影到原生 NPC 数据里
- 把服务器 WAR/PEACE 关系映射到本地原生 NPC 外交立场
- 发送、接受、继续战争请求和和平请求
- 战争只做服务器结算，不直接改双方城市、资源、军队或领土

强烈建议先用测试存档。`SHD` 影子 NPC 会通过游戏原生 faction API 改一个世界区域。

## 1. 安装 Mod

解压这个 zip 后，在当前目录打开 PowerShell，运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\install_mod.ps1
```

安装后应该出现：

```text
%APPDATA%\songsofsyx\mods\Syx Duo Realm\_Info.txt
%APPDATA%\songsofsyx\mods\Syx Duo Realm\V71\script\Syx Duo Realm.jar
```

然后启动 Songs of Syx V71，在 Mod 列表里启用 `Syx Duo Realm`。

如果你是从 Steam Workshop 订阅安装，则不需要运行 `install_mod.ps1`。但 Workshop 只负责安装 Mod，本地/LAN/Tailscale 测试服务器仍然需要另行运行。

## 2. 配置文件

第一次加载带 Mod 的存档后，会生成配置文件：

```text
%APPDATA%\songsofsyx\saves\profile\Syx Duo Realm\syx_duo_realm.json
```

两个人必须：

- 使用相同 `roomCode`
- 使用不同 `playerName`
- 使用同一个 `serverUrl`

示例：

```json
{
  "serverUrl": "http://100.x.y.z:8787/api/state",
  "playerName": "friend",
  "roomCode": "duo-test",
  "syncIntervalSeconds": 30,
  "tradeOfferResourceKey": "FISH",
  "tradeOfferAmount": 25,
  "tradeOfferToPlayer": "",
  "mirrorNativeDiplomacy": true,
  "nativePeaceStance": "TRADE"
}
```

`tradeOfferToPlayer` 留空时，Mod 会优先使用已绑定的影子好友，或者房间里检测到的好友。
`mirrorNativeDiplomacy` 为 true 时，Mod 会把服务器上的好友关系同步到本地影子 NPC 的原生外交状态。默认 `nativePeaceStance` 是 `TRADE`，更像可贸易 NPC；如果想更保守，可以改成 `NEUTRAL`。

如果要快速生成配置，可以直接运行：

主机：

```powershell
powershell -ExecutionPolicy Bypass -File .\configure_host_example.ps1
```

朋友：

```powershell
powershell -ExecutionPolicy Bypass -File .\configure_friend_example.ps1 -HostTailscaleIp "主机的100.x.y.z" -PlayerName "你的名字"
```

## 3. 最稳远程测试方案：Tailscale

推荐用 Tailscale，把两台电脑变成一个私有虚拟局域网。这样不用公网端口映射，也不用把测试服务器暴露到互联网。

步骤：

1. 两个人都安装 Tailscale，并加入同一个 tailnet。
2. 主机运行服务器：

```powershell
powershell -ExecutionPolicy Bypass -File .\start_tailscale_server.ps1
```

3. 主机查自己的 Tailscale IPv4：

```powershell
tailscale ip -4
```

4. 两个人都把 `serverUrl` 改成：

```text
http://主机的100.x.y.z:8787/api/state
```

5. 好友在自己电脑验证：

```powershell
powershell -ExecutionPolicy Bypass -File .\verify_server.ps1 -ServerUrl "http://100.x.y.z:8787/api/state"
```

能看到服务器健康信息后再进游戏。

## 4. 进游戏后的验证顺序

1. 加载测试存档。
2. 展开右上角 `DUO >`。
3. 等 `D OK`，表示本地导出和上传正常。
4. 点 `R` 或等待自动刷新，目标是 `R OK`。
5. 点一次 `SHD`，目标是 `SHD ON`。
6. 打开游戏原生世界/阵营 UI，找好友名对应的 NPC 势力。
7. 看 `SHD` 悬浮提示，确认 population、dominant race、resources、fresh 状态存在，并确认 `Mirrored: PEACE -> TRADE` 或 `WAR -> WAR`。
8. 可以测试贸易：
   - 发送方用 `RES` 选择资源
   - 发送方用 `AMT` 选择数量
   - 点 `SND`
   - 接收方看到待领取后，用 `N` 选择，点 `D/T` 领取
9. 可以测试战争：
   - 一方点 `WAR` 发送请求
   - 另一方看到 `WAR IN` 后点 `WAR` 接受并结算
   - `PCE` 用于请求或接受和平

重要：战争结算前双方最好都显示 `R OK`。如果某一方太久没同步，服务器会拒绝使用旧状态结算。

如果 `SHD` 显示 `SHD OLD`，说明好友 NPC 壳还在，但服务器上的好友状态已经过期。等好友重新同步，或者双方点 `D` 后再点 `R`。

## 5. 常见问题

### 游戏里看不到 Mod

确认目录层级是：

```text
%APPDATA%\songsofsyx\mods\Syx Duo Realm\_Info.txt
%APPDATA%\songsofsyx\mods\Syx Duo Realm\V71\script\Syx Duo Realm.jar
```

不是多套了一层 `mods` 或 zip 根目录。

### `R OLD` 或战争失败

说明有人状态过期。两边等待 30 秒，或点 `D` 强制同步，再点 `R` 刷新。

### 朋友连不上服务器

检查：

- 主机的 `start_server_host.ps1` 还在运行
- 好友能打开 `http://100.x.y.z:8787/api/health`
- 两边 Tailscale 在线
- Windows 防火墙没有拦截 Node.js 或 TCP 8787

### 不建议公网直连

这个测试服务器没有鉴权。不要把它直接暴露到公网长期运行。必须用公网/隧道时，至少设置：

```powershell
$env:ENABLE_DEV_ENDPOINTS = "0"
```

并使用随机 `roomCode`，测试完立刻关服。
