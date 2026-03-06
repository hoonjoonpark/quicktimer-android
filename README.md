# Quick Timer App

여러 개의 타이머를 추가/관리하고, 실행 기록을 확인할 수 있는 Android 타이머 앱입니다.
핵심 기능은 상주 알림(Notification)에서 자주 쓰는 타이머를 바로 시작할 수 있는 빠른 실행 경험입니다.

## 주요 기능
- 여러 타이머 프리셋 추가/수정/삭제/정렬
- 실행 중 타이머 목록 관리 (일시정지/재개/중지)
- 타이머 완료 및 실행 기록(History) 확인
- 상주 알림에서 타이머 빠른 시작 및 제어
- 설정: 언어(한국어/영어/일본어), 폰트 크기, 테마, 광고 제거 옵션

## 기술 스택
- Kotlin
- Jetpack Compose (Material 3)
- Android Foreground Service + Notification
- Room
- DataStore
- Coroutines

## 개발 환경
- Android Studio (최신 안정 버전 권장)
- JDK 17
- Android SDK
  - `compileSdk`: 35
  - `minSdk`: 28
  - `targetSdk`: 35

## 실행 방법
1. Android Studio에서 프로젝트를 엽니다.
2. Gradle Sync를 완료합니다.
3. 디바이스/에뮬레이터를 선택합니다.
4. `app` 모듈을 실행합니다.

CLI로 빌드할 경우:

```bash
./gradlew :app:assembleDevDebug
./gradlew :app:assembleProdRelease
# alias
./gradlew :app:assembleProdRel
```

## 광고 유닛 설정
- 광고 유닛은 flavor별 `BuildConfig`로 주입됩니다 (`dev`/`prod`).
- 값은 아래 우선순위로 읽습니다:
  1. 프로젝트 루트 `local.properties` (gitignore, 개인/로컬용)
  2. 프로젝트 루트 `gradle.properties` (프로젝트 공통값)
  3. CI 환경변수
- 키:
  - `ADMOB_DEV_BANNER_UNIT_ID`
  - `ADMOB_DEV_INTERSTITIAL_UNIT_ID`
  - `ADMOB_PROD_BANNER_UNIT_ID`
  - `ADMOB_PROD_INTERSTITIAL_UNIT_ID`
- 키가 없으면 Google 테스트 유닛으로 fallback 됩니다.

## 프로젝트 구조
- `app/src/main/java/com/quicktimer`
  - `ui`: Compose 화면 및 ViewModel
  - `service`: 포그라운드 서비스/타이머 런타임 로직
  - `data`: Room, DataStore, 모델/스토어
- `app/src/main/res`: 리소스 (아이콘/문자열/테마)
- `app/src/main/assets`: 테마 설정 파일

## GitHub 업로드 전 체크
- `local.properties`, `build/`, `.gradle/`, `*.apk` 등 로컬/빌드 산출물은 `.gitignore`로 제외합니다.
- 이미 Git 추적 중인 빌드 산출물은 `git rm --cached`로 인덱스에서 제거한 뒤 커밋하세요.

## 라이선스
필요 시 `LICENSE` 파일을 추가해 사용 라이선스를 명시하세요.
