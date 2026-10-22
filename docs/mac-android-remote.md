# Mac + Android remote architecture

This deployment uses two Android roles so that remote access does not remove the phone that performs automation:

| Device | Location | Role |
| --- | --- | --- |
| Mac | Home | Runs the Host, Codex bridge, SQLite state and the Cloudflare Relay connection. |
| Dedicated Android | Home, powered and on Wi-Fi | Executes phone tasks through Wireless ADB. |
| Personal Android | With the owner | Connects to the Host through the Relay for chat, task status and approvals. |

## Why Wireless ADB

The existing `orbit_phone` service already performs the automation safely through ADB. Android 11+ supports pairing and connecting over the local network, so no USB data cable is required. The Host stores the selected address with:

```bash
opa config phone serial <ip:port>
```

The address is used by the Host, phone worker and phone-notification watcher after the next Host start. `OPA_PHONE_SERIAL` remains an override for testing or a temporary device.

## Remote client scope

The forthcoming Android companion is a separate client from the dedicated automation phone. It will use the existing encrypted protocol and Relay, and provide:

1. QR/link pairing with short-code verification.
2. Remote chat, streaming replies, task status and approval decisions.
3. Android notifications for tasks that need attention.

It will not expose raw ADB or phone-automation controls to the internet. The Host continues to decide what a phone worker may do, and the dedicated phone remains local to the home network.
