# Market Ledger 2040 모드 사양

모드는 사용자 앱 데이터의 `mods` 폴더에 설치한다. 실행 위치나 설치 디렉터리를 기준으로 삼지
않으므로 업데이트와 실행 방법이 달라져도 같은 모드 목록을 사용한다.

| 운영체제 | 모드 폴더 |
|---|---|
| Windows | `%APPDATA%/MarketLedger2040/mods` |
| 그 외 | `~/.market-ledger-2040/mods` |

로비의 **모드** 메뉴에서 폴더를 열고 다시 검색할 수 있다. 활성화 여부와 사용자 설정은 모드
원본과 분리해 저장된다. 로비에서 바꾼 활성 모드 집합은 다음 새 게임부터 적용되며, 모드 ID,
버전, 설정값은 저장 게임에 함께 기록된다. 저장 게임이 요구하는 모드가 없거나 버전이 다르면
불러오기를 거부한다.

## 폴더 구조

모드 ID가 `exmp`라면 최소 구조는 다음과 같다.

```text
mods/
└── exmp/
    ├── manifest.xml
    └── cover.png
```

- 폴더 이름과 manifest의 `id`는 같아야 한다.
- ID는 소문자 영문·숫자로 시작하고 이후 소문자 영문·숫자·`.`·`_`·`-`만 사용할 수 있다.
- 커버는 `png`, `jpg`, `jpeg`, `webp`를 지원한다. manifest에서 파일을 지정하지 않으면
  `cover.*`를 찾으며 후보가 여러 개면 모드를 불러오지 않는다.
- manifest와 커버는 일반 파일이어야 하며 심볼릭 링크는 허용하지 않는다.

## manifest.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<mod schemaVersion="1" apiVersion="1" id="exmp">
    <name>예제 시장 모드</name>
    <description>시장 환경과 플레이 규칙을 조절하는 예제입니다.</description>
    <author>Example Studio</author>
    <version>1.0.0</version>
    <lastModified>2026-08-11</lastModified>
    <cover>cover.png</cover>

    <permissions>
        <permission>game.read</permission>
        <permission>game.playerCommands</permission>
        <permission>game.marketControl</permission>
    </permissions>

    <settings>
        <setting key="showHints" type="boolean">
            <name>시장 힌트</name>
            <description>중요한 시장 변화를 더 자세히 표시합니다.</description>
            <default>true</default>
        </setting>

        <setting key="shockScale" type="decimal">
            <name>충격 배율</name>
            <description>외부 사건이 시장에 미치는 강도입니다.</description>
            <default>1.0</default>
            <min>0.5</min>
            <max>2.0</max>
        </setting>

        <setting key="pace" type="enum">
            <name>시장 속도</name>
            <description>모드가 사용할 기본 진행 성향입니다.</description>
            <default>normal</default>
            <option value="calm">완만</option>
            <option value="normal">보통</option>
            <option value="volatile">격변</option>
        </setting>
    </settings>
</mod>
```

설정 타입은 `boolean`, `integer`, `decimal`, `string`, `enum`이다. 숫자 타입은 선택적으로
`min`과 `max`를 받을 수 있고, `enum`은 하나 이상의 `option`이 필요하다. 기본값은 선언된
타입·범위·선택지에 맞아야 한다. `integer`는 정확한 범위 비교를 위해 IEEE-754 안전 정수 범위
(-9,007,199,254,740,991~9,007,199,254,740,991) 안에서 사용한다.

## API 권한

| manifest 값 | 범위 |
|---|---|
| `game.read` | 게임 시각, 단계, 종목·시세, 지수, 현금·보유, 주문·체결, 뉴스, 거시 환경, 장 상태, 포트폴리오 조회 |
| `game.playerCommands` | 화면·종목·턴 선택, 진행, 주문·취소, 환전, 읽음·관심종목, 일시정지·재개 |
| `game.marketControl` | 외부 시장 환경 목표 변경 |
| `game.debugConsole` | 앱에 컴파일된 기본 제공 개발자 콘솔 호스트용 권한. 제3자 모드에는 실행 기능을 부여하지 않음 |
| `game.contentRegister` | 향후 새 게임 콘텐츠 등록 런타임용 예약 권한 |
| `storage.modState` | 향후 모드별 저장 상태용 예약 권한 |

공개 API 버전은 `MOD_API_VERSION`으로 확인한다. 신뢰된 호스트는
`SimulatorGameModApi`를 만들고 manifest에서 허용된 권한만 전달한다. 모드는
`query.snapshot()`으로 분리된 불변 스냅샷을 읽고, suspend 함수인 `commands.execute(...)`로
명령을 보낸다. 명령은 게임 런타임 스레드에 직렬화되며 권한, 진행 중 동시 변경, 입력값과 엔진
불변식을 검사한 뒤 성공·거부·실패를 구조화해 반환한다. 내부 `SimulatorUiState` 전체를 받는
API는 가변 객체 별칭으로 런타임 소유권과 직렬화 경계를 우회할 수 있어 공개하지 않는다.
`events`는 게임 시작, 상태 변경, 턴
완료, 최종 정산 진입, 게임 종료를 최대 64개 전이의 제한된 버퍼로 전달하며, 느린 구독자는
오래된 전이부터 놓칠 수 있다.

manifest 권한은 모드의 요청 목록일 뿐 자동 승인 목록이 아니다. 실행 호스트를 추가할 때는
사용자가 승인한 권한 집합을 모드 ID·버전에 묶어 별도로 저장하고, 요청 권한과 승인 권한의
교집합만 `SimulatorGameModApi`에 전달해야 한다.

## 실행 코드에 대한 신뢰 경계

현재 모드 검색기는 manifest, 커버, 설정을 적재하며 임의의 JAR이나 스크립트를 자동 실행하지
않는다. 같은 JVM 프로세스에 올린 제3자 코드는 파일·네트워크·프로세스·리플렉션 접근을 API
권한으로 차단할 수 없기 때문이다. API 권한은 게임 기능에 대한 논리 경계이지 JVM 샌드박스가
아니다.

기본 제공 모드나 별도로 신뢰한 실행 호스트는 위 API를 바로 사용할 수 있다. 제3자 실행 모드를
지원할 때는 이 API 뒤에 별도 프로세스와 제한된 IPC, 서명·신뢰 확인, 시간·메모리·명령 수 제한을
추가해야 한다. 로컬 HTTP 서버나 무제한 ClassLoader 실행은 모드 형식의 일부로 간주하지 않는다.

## 기본 제공 개발자 콘솔

앱은 첫 실행 시 `market-ledger.debug` 모드를 모드 폴더에 안전하게 적재한다. 기본값은 비활성이며,
로비의 **모드** 메뉴에서 활성화한 뒤 새 게임을 시작해야 해당 캠페인에 고정된다. 게임 중 물리
키보드의 백틱(`` ` ``)을 누르면 콘솔이 열리고, `help`를 입력하면 현재 버전의 명령과 정확한
인자 형식을 확인할 수 있다.

대표 명령은 다음과 같다.

- `turn jump <turn|max> [--reset]`, `turn cancel`: 정상 시간 진행 경로로 턴 이동 및 취소
- `price set <instrument> <amount> <native|krw|usd>`, `price change <instrument> <percent>`
- `cash add|set <krw|usd> <amount>`, `fx set <usdKrw>`, `fx change <percent>`
- `ending settle|finish`: 남은 일정을 계산해 정산 진입 또는 종료
- `value get|set|add <path> [number]`: `cash.*`, `fx.usdkrw`, `price.*`, `force.*` 허용 목록만 조작
- `rule set <fractional|auto_exchange|ironman> <on|off>`, `force set <name> <0..1>`
- `event list [filter]`, `event describe <templateId>`, `event trigger <templateId> [target]`
- `stocks`, `stock`, `status`, `orders cancel-all`, `pause`, `resume`, `save-check`, `clear`

과거 턴 이동은 현재 진행을 보존한 채 시간을 되감지 않는다. 데이터 손실을 명시하는 `--reset`을
붙였을 때만 같은 시드와 옵션으로 게임을 다시 만들고 목표 턴까지 재생한다. 즉시 정산도 게임
시각만 바꾸지 않고 세금·기업행동·이벤트를 포함한 시간 진행을 수행하므로 계산 중에는 `turn
cancel`로 중단할 수 있다.

가격·현금·환율·규칙 변경은 앱 내부의 타입이 지정된 디버그 명령으로만 실행되며, 매 변경 뒤
저장 불변식 검사를 통과하지 못하면 직전 스냅샷으로 되돌린다. `value`는 reflection이나 임의
속성 경로를 받지 않는다. 이벤트도 원시 payload를 받지 않고 기본 템플릿 ID와 유효 대상만 받아
발생 확률을 우회하며, 중복 활성 이벤트·쿨다운·one-shot 규칙은 유지한다.

이 모드는 로컬 개발·QA용 치트 도구다. 활성화한 캠페인의 공정성은 보장하지 않으며, manifest의
`game.debugConsole` 요청만으로 제3자 코드가 콘솔 호스트나 내부 런타임 접근 권한을 얻지는 않는다.

## 적재 제한

- manifest 최대 256 KiB, 엄격한 UTF-8
- DTD, 외부 엔티티, 외부 스키마, 알 수 없거나 중복된 요소 거부
- 커버 최대 8 MiB, 최대 4096×4096
- 경로 정규화 후 모드 루트 밖으로 나가는 파일 거부
- 손상된 모드는 항목별 오류로 격리하고 정상 모드 목록은 계속 표시
