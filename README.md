# Yoman

Voice-first AI journaling for iOS. Tap record, talk for 1-5 minutes, get back a clean structured diary entry with summary, work/personal sections, people, mood, and tags. Searchable across all entries.

See [`ROADMAP.md`](../Downloads/yoman/ROADMAP.md) and [`ARCHITECTURE.md`](../Downloads/yoman/ARCHITECTURE.md) for full design.

## Repo layout

- `backend/` — Spring Boot 3.5 (Java 17). REST API.
- `mobile/` — Expo (React Native, TypeScript). iOS-first.

## Run locally

**Backend** (terminal 1):
```
cd backend
./mvnw spring-boot:run        # macOS/Linux
./mvnw.cmd spring-boot:run    # Windows (or .\mvnw.cmd from PowerShell)
```
Health check: `curl http://localhost:8080/health` → `{"status":"ok","service":"yoman-backend"}`

**Mobile** (terminal 2):
```
cd mobile
npx expo start
```
Open Expo Go on your iPhone (same Wi-Fi as the laptop) and scan the QR code. Tap the button on the screen — it hits the backend on the LAN IP hardcoded in `App.tsx`.

If the phone fetch hangs, check:
1. Phone and laptop are on the same Wi-Fi (not phone hotspot / VPN)
2. Windows Defender Firewall isn't blocking inbound TCP 8080
3. The LAN IP in `mobile/App.tsx` matches `ipconfig` output

## Roadmap status

Weekend 1: ✅ Backend `/health` + Expo skeleton + phone round-trip
