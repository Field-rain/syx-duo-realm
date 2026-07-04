# Syx Duo Realm SteamCMD 上传方案

Discord 验证卡住或找不到 Songs of Syx Workshop Uploader 时，可以用 SteamCMD 上传。

Steam 官方 Workshop 文档支持：

```text
workshop_build_item <vdf file>
```

新建 Workshop item 时，`publishedfileid` 使用 `0`。更新已有 item 时，改成已有 Workshop item id。

## 一键新建 Hidden Item

在当前目录打开 PowerShell：

```powershell
powershell -ExecutionPolicy Bypass -File .\upload_new_hidden.ps1 -SteamUser "你的Steam用户名"
```

脚本会：

1. 下载 SteamCMD 到本目录的 `steamcmd` 文件夹。
2. 生成 `syx_duo_realm_workshop.vdf`。
3. 用 `publishedfileid = 0` 创建新 Workshop item。
4. 可见性使用 `2`，也就是 Hidden/Private。

SteamCMD 会要求你输入 Steam 密码和 Steam Guard 验证码。脚本不会保存你的密码。

## 更新已有 Item

第一次上传成功后，SteamCMD 输出里会出现新的 `PublishedFileId`。之后更新时运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\upload_update_existing.ps1 -SteamUser "你的Steam用户名" -PublishedFileId "1234567890"
```

## 修复描述乱码

如果 Workshop 页面描述显示很多 `????` 或字面量 `\n`，运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\update_description_only.ps1 -SteamUser "你的Steam用户名" -PublishedFileId "1234567890"
```

这个脚本会使用英文 ASCII-safe 描述重新更新页面，避免 SteamCMD/VDF 编码把中文破坏。

也可以从 Workshop 页面 URL 里拿 id：

```text
https://steamcommunity.com/sharedfiles/filedetails/?id=1234567890
```

## 上传内容

脚本会上传：

```text
..\SyxDuoRealm-V71-Workshop\content
```

这个目录必须直接包含：

```text
_Info.txt
V71
```

预览图：

```text
..\SyxDuoRealm-V71-Workshop\preview.png
```

## 上传后检查

1. 打开 Workshop 页面。
2. 确认标题、预览图、描述正常。
3. 先保持 Hidden 或 Friends-only。
4. 自己订阅测试。
5. 确认游戏里能看到 Syx Duo Realm 后再给朋友测试。
