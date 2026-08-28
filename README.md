# Just Chat

A persistent, feature-rich chat system that replaces Minecraft's default player chat.

**Just Chat**은 Minecraft의 기본 플레이어 채팅을 확장해, 서버에 채팅 기록을 영구 저장하고
플레이어 태그, 아이템 태그, 읽음 상태, 과거 기록 탐색 등을 제공하는 NeoForge 모드입니다.

## Compatibility

- Minecraft **26.1.2**
- NeoForge **26.1.2.99+**
- Just Chat **1.0.0**
- Mod ID: `njw_just_chat`
- Build environment: **Java 25**
- Languages: **한국어 / English**

멀티플레이에서는 클라이언트와 서버에 **동일한 버전의 Just Chat**을 설치하세요.

## Features

### Persistent chat history

- 플레이어 채팅을 서버 월드에 영구 저장합니다.
- 주요 글로벌 시스템 메시지도 채팅 기록에 함께 표시할 수 있습니다.
- 서버 재시작 후에도 이전 채팅 기록을 다시 불러올 수 있습니다.
- 오래된 기록과 새로운 기록을 양방향으로 탐색할 수 있습니다.
- 대량의 기록은 segment 단위로 관리해 장기간 서버에서도 사용할 수 있도록 설계했습니다.

### Custom chat screen

- `T` 키로 Just Chat 화면을 엽니다.
- `/` 키는 Minecraft의 명령어 입력 화면을 사용합니다.
- 일반 채팅과 명령어 입력을 분리해 실수로 명령어를 일반 채팅으로 전송하는 것을 방지합니다.
- 과거 기록을 보고 있을 때 새 메시지가 도착하면 알림을 표시합니다.
- `↓` 버튼으로 최신 메시지로 빠르게 이동할 수 있습니다.

### Player mentions

- `@PlayerName` 형식으로 플레이어를 태그할 수 있습니다.
- 온라인 플레이어를 우선해서 추천합니다.
- 이전에 알려진 오프라인 플레이어도 추천할 수 있습니다.
- 태그된 플레이어에게 mention 알림을 표시합니다.
- 플레이어의 온라인 상태와 마지막 접속 시간을 확인할 수 있습니다.

### Item tags

- 인벤토리의 아이템을 채팅에 태그할 수 있습니다.
- 서버가 실제 아이템 스냅샷을 기준으로 태그를 검증합니다.
- 일반 클라이언트에서는 서버가 확인한 실제 아이템 이름을 기준으로 표시합니다.

### Message deletion

- 자신이 보낸 메시지는 전송 후 제한된 시간 동안 삭제할 수 있습니다.
- 삭제된 메시지는 원문과 태그 정보를 제거한 상태로 기록에 남습니다.

### Read state

- 플레이어별 마지막 읽은 메시지 위치를 서버에 저장합니다.
- 읽지 않은 메시지가 있으면 읽음 경계를 표시합니다.
- 재접속 후에도 읽음 상태를 이어서 사용할 수 있습니다.

### Client configuration

현재 제공되는 클라이언트 설정:

- **Close chat after sending**
    - 메시지 전송 후 채팅 화면을 자동으로 닫을지 설정합니다.

### Server-side protection

- 채팅 전송, 삭제, history 요청, 플레이어 추천 및 상태 조회에 rate limit을 적용합니다.
- history 요청에는 request ID를 사용해 timeout 이후 늦게 도착한 오래된 응답을 무시합니다.
- 초기 history 요청이 timeout된 경우 안전하게 다시 요청할 수 있습니다.

## Installation

1. Minecraft **26.1.2**에 맞는 NeoForge **26.1.2.99 이상**을 설치합니다.
2. GitHub Releases에서 `njw_just_chat-1.0.0.jar`를 다운로드합니다.
3. 다운로드한 JAR 파일을 클라이언트의 `mods` 폴더에 넣습니다.
4. 멀티플레이 서버에서도 서버의 `mods` 폴더에 같은 버전의 JAR을 넣습니다.
5. Minecraft를 실행합니다.

> GitHub가 자동으로 제공하는 `Source code (zip)` 또는 `Source code (tar.gz)`는 설치용 모드 파일이 아닙니다.
> Release의 **Assets**에 첨부된 `.jar` 파일을 사용하세요.

## Usage

### Chat

- `T`: Just Chat 열기
- `Enter`: 메시지 전송
- `/`: Minecraft 명령어 입력

Just Chat 화면에서는 `/`로 시작하는 메시지를 일반 채팅으로 전송하지 않습니다.

### Player tag

채팅 입력 중 `@` 뒤에 플레이어 이름을 입력하면 추천 목록이 표시됩니다.

```text
@PlayerName 안녕하세요!