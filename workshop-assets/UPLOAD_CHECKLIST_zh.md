# Syx Duo Realm Workshop 上传清单

## 上传前

1. 确认游戏版本是 Songs of Syx V71。
2. 运行 `.\mvnw.cmd install`，确认构建成功。
3. 确认安装目录里的 jar 和 `target\Syx Duo Realm.jar` 哈希一致。
4. 用测试服跑一次：
   - `/api/health`
   - `/api/state`
   - `/api/room_status`
   - `/api/friend_state`
5. 进游戏至少确认：
   - `DUO >` 能展开
   - `D OK`
   - `R OK` 或 `R OLD`
   - 单人 dev friend 下 `SHD ON`

## Workshop Uploader 内容

标题：

```text
Syx Duo Realm
```

内容目录：

```text
workshop\SyxDuoRealm-V71-Workshop\content
```

缩略图：

```text
workshop\SyxDuoRealm-V71-Workshop\preview.png
```

中文描述：

```text
workshop-assets\description_zh.txt
```

英文描述：

```text
workshop-assets\description_en.txt
```

更新日志：

```text
workshop-assets\changenotes_0.2.0.txt
```

建议可见性：

```text
Friends-only 或 Hidden 先测，确认无误后再 Public。
```

## 上传后

1. 自己取消本地手动安装版本，避免和 Workshop 版本混淆。
2. 订阅 Workshop 版本。
3. 启动游戏并确认 Mod 列表显示 Syx Duo Realm。
4. 用测试存档验证 UI、导出和房间状态。
5. 再让朋友订阅并测试。

## 注意

Workshop 只分发 Mod 本体，不会自动运行 Node.js 服务器。朋友仍需使用 release 包里的服务器脚本，或连接到你/Tailscale 上运行的服务器。

