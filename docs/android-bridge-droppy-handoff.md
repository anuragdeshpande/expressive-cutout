# Android Bridge: Mac Companion (Droppy) Architecture & Protocol Handoff

## Executive Summary

The Android counterpart for **Android Bridge** has been implemented in `expressive-cutout` under `feat/android-bridge-pairing`. This document provides the architectural evaluation, exact wire protocol specifications, and implementation guidelines for the macOS Droppy companion agent working on:

- **Ticket**: [android-bridge #15](https://github.com/anuragdeshpande/android-bridge/issues/15)
- **Target Component**: `Sources/AndroidBridge/AndroidBridgeDroplet.swift` in `anuragdeshpande/android-bridge`

---

## 1. Wire Protocol Specification

Both devices operate on a zero-cloud, direct local network (LAN) connection secured by TLS 1.3 with certificate fingerprint pinning.

### 1.1 QR Code Bootstrap URI Format

When the user initiates pairing from the Droppy solo shelf widget, Droppy generates an ephemeral, single-use URI with a 5-minute expiry:

```
androidbridge://pair?v=1.0&service=<MacServiceName>&endpoint=<IP:Port>&fingerprint=<CertFingerprintHex>&challenge=<Base64URLChallenge>&expires=<EpochSeconds>
```

| Parameter | Type | Description | Example |
| :--- | :--- | :--- | :--- |
| `v` | String | Protocol version (must start with `1.`) | `1.0` |
| `service` | String | Bonjour service instance name | `Droppy-MacBook-Pro` |
| `endpoint` | String | Current LAN IPv4/IPv6 and listening port | `192.168.1.105:8765` |
| `fingerprint` | String | 64-character lowercase hex SHA-256 of Droppy's TLS certificate | `a1b2c3d4e5f6...` |
| `challenge` | String | 32-byte cryptographically secure random challenge (Base64URL) | `dGVzdF9jaGFsbGVuZ2Vf...` |
| `expires` | Long | Expiration timestamp in epoch seconds | `1700000300` |

### 1.2 DNS-SD / Bonjour Advertisement

Droppy advertises on the LAN using Apple's `NWListener` / Bonjour:
- **Service Type**: `_androidbridge._tcp`
- **Domain**: `local.`
- **Service Name**: `<service>` from pairing offer (e.g., `Droppy-MacBook-Pro`)
- **TXT Record (Improvement)**:
  - `v=1.0`
  - `fp=<fingerprint_prefix_8chars>`
  *(Enables Android to identify the peer during reconnection without probe overhead).*

### 1.3 Transport & TLS 1.3

1. **Protocol**: WebSocket over TLS 1.3 (`wss://<endpoint>/bridge`).
2. **Certificate**: Droppy serves a self-signed X.509 certificate. The SHA-256 hash of the DER-encoded certificate matches the `fingerprint` query parameter in the QR code.
3. **Pinning**: Android bypasses traditional CA root stores and pins the connection directly to this SHA-256 fingerprint.

---

## 2. Handshake & Framing Flow

Messages are UTF-8 JSON envelopes with the following schema:
```json
{
  "type": "<message_type>",
  "id": "<uuid_v4>",
  "payload": { ... }
}
```

### Phase 1: Hello & Identity Exchange

1. **Android -> Droppy (`pair_hello`)**:
   ```json
   {
     "type": "pair_hello",
     "id": "e9b251a2-1596-4f40-84eb-4752b0df3c12",
     "payload": {
       "protocolVersion": "1.0",
       "androidIdentity": "<base64_der_ec_p256_public_key>",
       "deviceName": "Pixel 9 Pro",
       "signedChallenge": "<base64_der_ecdsa_signature_of_challenge>"
     }
   }
   ```

2. **Droppy -> Android (`pair_welcome`)**:
   - Droppy verifies the ECDSA signature against the challenge using `androidIdentity`.
   - Droppy replies with its identity and name:
   ```json
   {
     "type": "pair_welcome",
     "id": "4a71c841-3b76-4fbc-bb12-92fa023e1e55",
     "payload": {
       "macIdentity": "<base64_der_ec_p256_public_key>",
       "deviceName": "MacBook Pro",
       "signedChallenge": "<base64_der_ecdsa_signature_by_mac>"
     }
   }
   ```

### Phase 2: Short Authentication String (SAS) Derivation

Both devices independently derive the 6-digit confirmation code:

$$\text{Digest} = \text{HMAC-SHA256}(\text{key} = \text{challengeBytes}, \text{data} = \text{macPublicKeyDER} \parallel \text{androidPublicKeyDER})$$
$$\text{Code} = |\text{first\_4\_bytes\_as\_big\_endian\_int}| \pmod{1\,000\,000}$$
$$\text{Display Code} = \text{String.format}("\%06\text{d}", \text{Code})$$

Both screens display the derived 6 digits in high-contrast chips.

### Phase 3: Mutual Confirmation & Persistence

1. **Android -> Droppy (`pair_confirm`)**:
   When the user taps "Numbers Match" on Android:
   ```json
   {
     "type": "pair_confirm",
     "id": "6c4912fa-7f89-4cb5-b44c-353d9e8432a1",
     "payload": {
       "code": "482910"
     }
   }
   ```

2. **Droppy -> Android (`pair_ack`)**:
   When the user confirms on macOS and Android's code matches:
   ```json
   {
     "type": "pair_ack",
     "id": "7b823e12-4211-4091-a1b2-847291a82341",
     "payload": {
       "status": "ok"
     }
   }
   ```
   - Both devices write the pairing record to secure storage (macOS Keychain, Android DataStore + KeyStore).
   - UI on both devices transitions to `Paired · Connected`.

---

## 3. Session Reconnection & Heartbeat

Once paired, the devices reconnect without scanning a QR code:

1. **Android -> Droppy (`session_hello`)**:
   ```json
   {
     "type": "session_hello",
     "id": "<uuid>",
     "payload": {
       "peerId": "<mac_fingerprint>",
       "androidIdentity": "<base64_der_public_key>",
       "signedTimestamp": "<base64_signature_of_timestamp>",
       "timestamp": 1700000350000
     }
   }
   ```
2. **Droppy -> Android (`session_ack`)**:
   ```json
   {
     "type": "session_ack",
     "id": "<uuid>",
     "payload": {
       "status": "ok"
     }
   }
   ```
3. **Heartbeat**: Droppy or Android sends periodic `ping` (every 15–20 seconds); the receiver immediately returns `pong`.

---

## 4. Architectural Evaluation & Recommendations for Droppy

### 4.1 Native macOS QR Generation
In `AndroidBridgeDroplet.swift`, avoid third-party libraries for QR rendering. macOS has native CoreImage acceleration:
```swift
import CoreImage.CIFilterBuiltins

func generateQRCode(from uriString: String) -> NSImage? {
    let filter = CIFilter.qrCodeGenerator()
    filter.message = Data(uriString.utf8)
    filter.correctionLevel = "M"
    guard let ciImage = filter.outputImage else { return nil }
    let rep = NSCIImageRep(ciImage: ciImage.transformed(by: CGAffineTransform(scaleX: 8, y: 8)))
    let nsImage = NSImage(size: rep.size)
    nsImage.addRepresentation(rep)
    return nsImage
}
```

### 4.2 Network Framework Listener (`NWListener`)
Use `Network.framework` for modern, async TLS 1.3 and WebSocket handling:
```swift
let parameters = NWParameters.tls
// Configure sec_protocol_options with self-signed identity from Keychain
let wsOptions = NWProtocolWebSocket.Options()
parameters.defaultProtocolStack.applicationProtocols.insert(wsOptions, at: 0)
let listener = try NWListener(using: parameters, on: NWEndpoint.Port(rawValue: 8765)!)
listener.service = NWListener.Service(name: serviceName, type: "_androidbridge._tcp")
```

### 4.3 App Nap & Background Execution
Because Droppy runs as an accessory/shelf droplet without a permanent foreground window, macOS App Nap may throttle background network listeners.
- Call `ProcessInfo.processInfo.beginActivity(options: [.userInitiated, .idleSystemSleepDisabled], reason: "Android Bridge Session")` during active pairing or live sessions.
- In `deactivate()`, end the activity to preserve energy.

### 4.4 Info.plist Entitlements & Permissions
Ensure Droppy's bundle declares:
- `NSLocalNetworkUsageDescription`: "Droppy connects to your paired Android phone over your local Wi-Fi network."
- `NSBonjourServices`: `["_androidbridge._tcp"]`
Without these, macOS 14+ Sequoia/Sonoma will block socket binding silently.

### 4.5 Keychain Item Configuration
When saving the Mac identity keypair:
- Use `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`.
- Do not mark synchronizable to iCloud (`kSecAttrSynchronizable: false`), ensuring the identity remains hardware-bound to the local Mac.

---

## 5. Summary of Android Implementation Details

| Layer | Implementation Class | Location |
| :--- | :--- | :--- |
| **Model** | `PairingOffer`, `PairingOfferParser` | `com.ekoehler.expressivecutout.bridge.model` |
| **Security** | `BridgeIdentityStore` (EC P-256), `ShortCodeDerivation` | `com.ekoehler.expressivecutout.bridge.security` |
| **Data** | `BridgePairingStore` (DataStore) | `com.ekoehler.expressivecutout.bridge.data` |
| **Transport** | `BridgeSessionClient` (OkHttp TLS 1.3), `BridgeTlsSocketFactory` | `com.ekoehler.expressivecutout.bridge.transport` |
| **Discovery** | `BridgeNsdResolver` (mDNS) | `com.ekoehler.expressivecutout.bridge.discovery` |
| **UI** | `BridgePairingScreen`, `BridgePairingViewModel`, `CameraQrScannerView` | `com.ekoehler.expressivecutout.bridge.ui` |
| **Navigation** | `IntegrationsTab`, `MainScreen` | `com.ekoehler.expressivecutout.ui` |
