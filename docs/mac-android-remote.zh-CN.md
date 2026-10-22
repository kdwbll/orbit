# Orbit：Mac + 两台安卓手机使用说明

这份说明适用于你现在的配置，不需要 iPhone，也不需要数据线。

| 设备 | 放置位置 | 用途 |
| --- | --- | --- |
| Mac | 家里，接电、联网 | 运行 Orbit 主机和 Codex，是“大脑”。 |
| 备用安卓机 | 家里，接电、连家里 Wi-Fi | 被 Mac 通过无线 ADB 操作，是“手”。 |
| 随身安卓机 | 随身携带，用 Wi-Fi 或流量 | Orbit 客户端，用来聊天、看状态和审批。 |

## 你现在已经完成的部分

- Mac 已装好 Node、ADB、Android SDK，并已启动 Orbit Host。
- 备用安卓机已通过无线调试连接到 Mac，ADB Keyboard 已安装。
- Cloudflare Relay 已部署，Mac 可以通过它接受外网客户端连接。
- Android 客户端测试包已编译完成。

请让 **Mac 保持开机且不休眠**、备用安卓机保持充电并连着家里 Wi-Fi。Mac 上的代理程序也不要关闭，否则 Relay 可能无法连接。

## 第一次在随身安卓机上安装客户端

测试安装包在 Mac 的这个位置：

```
/Users/Apple/Documents/ChatGPT/第三大脑/orbit/android/app/build/outputs/apk/debug/app-debug.apk
```

因为没有数据线，最简单的传送方式是：在 Mac 的微信里把这个 APK 发给自己的微信文件传输助手，再在随身安卓机的微信中下载并安装。

安装时安卓系统可能询问“是否允许此来源安装未知应用”。只对微信（或你实际用来下载 APK 的应用）开启一次即可。这个版本是测试版，图标名称为 **Orbit**。

## 第一次配对

1. 在 Mac 打开终端，运行：

   ```bash
   cd /Users/Apple/Documents/ChatGPT/第三大脑/orbit/host
   npx tsx src/cli.ts pair
   ```

2. 终端会打印一个以 `opa://pair?d=` 开头的长链接。复制整段链接，通过微信文件传输助手发到随身安卓机。链接只在约 5 分钟内有效。
3. 在随身安卓机打开 Orbit，把整段链接粘贴进输入框，点“开始配对”。
4. 安卓机会显示 6 位校验码；Mac 终端也会显示同一组代码并询问是否一致。
5. 只要两边数字完全相同，就在 Mac 输入 `y` 再按回车。看到“已配对”即完成。

配对成功后，客户端会记住这台 Mac。以后打开 Orbit 会自动连接，**不需要再次执行配对**。

## 出门后如何使用

1. 不要关闭家里的 Mac、备用安卓机和它们的 Wi-Fi/电源。
2. 随身安卓机可以切到手机流量；打开 Orbit，顶部显示“已连接”后即可发消息。
3. Orbit 需要执行敏感动作时，会在客户端显示“同意”和“拒绝”；确认内容无误后再点“同意”。

Cloudflare Relay 只转发加密数据；聊天内容和审批内容由 Mac 与你的安卓客户端加密处理。

## 常见问题

### App 显示“家里的 Mac 当前离线”

先确认 Mac 开着、能上网；再在 Mac 终端运行：

```bash
cd /Users/Apple/Documents/ChatGPT/第三大脑/orbit/host
npx tsx src/cli.ts status
```

如果 Relay 地址为空或报错，请不要反复配对，先检查 Mac 上的代理程序是否仍开启。

### 备用安卓机重启后不能自动化

无线 ADB 的端口可能变化。重新在备用安卓机打开“开发者选项 → 无线调试”，查看新的 IP:端口；然后在 Mac 运行：

```bash
adb connect <新的IP:端口>
cd /Users/Apple/Documents/ChatGPT/第三大脑/orbit/host
npx tsx src/cli.ts config phone serial <新的IP:端口>
```

### 配对链接过期

在 Mac 再执行一次 `npx tsx src/cli.ts pair`，生成新链接即可。

## 当前测试版范围

已完成加密配对、自动重连、聊天基础界面和审批按钮。它还不是正式应用商店版本：测试 APK 需要手动安装，且首次真实配对仍需要进行一次现场验证。
