이 프로젝트는 한국어 음성을 텍스트로 변환하여 날짜/시간 순서대로 저장하고 보여주는 안드로이드 앱 예제입니다.

## 주요 기능
- `SpeechRecognizer`를 활용한 한국어 실시간 음성 인식
- 인식된 텍스트를 SharedPreferences에 JSON 형태로 저장
- 가장 최근 항목이 위에 오도록 날짜/시간 역순 정렬
- Jetpack Compose 기반의 간단한 카드 UI

## 빌드 및 실행 방법
1. Android Studio Iguana(또는 호환 버전)를 설치합니다.
2. 이 저장소를 클론한 후 Android Studio에서 "Open"으로 `App_coding2` 폴더를 엽니다.
3. 필요한 경우 Gradle 동기화를 진행합니다.
4. 안드로이드 8.0(API 26) 이상 기기 또는 에뮬레이터를 연결한 뒤 `Run`을 실행합니다.
5. 앱 실행 후 마이크 권한을 허용하고 "인식 시작" 버튼을 눌러 음성을 입력하면 텍스트로 저장됩니다.

## APK 직접 만들기
이미 완성된 실행 파일(APK)을 제공할 수는 없지만, 아래 절차로 직접 생성할 수 있습니다.
1. **Android SDK와 JDK가 포함된 개발 환경**(Android Studio 또는 독립형 SDK)을 준비합니다.
2. 필요한 경우 `local.properties` 파일을 루트 디렉터리에 생성하고, 설치된 SDK 경로를 `sdk.dir=/path/to/Android/Sdk` 형태로 지정합니다.
3. 터미널에서 프로젝트 루트로 이동한 뒤 다음 명령어를 실행합니다.
   - 디버그 빌드: `gradle assembleDebug`
   - 릴리스 빌드: `gradle assembleRelease`
   (Gradle Wrapper를 사용하고 있다면 `./gradlew`로 대체합니다.)
4. 빌드가 완료되면 다음 경로에서 APK 파일을 찾을 수 있습니다.
   - 디버그: `app/build/outputs/apk/debug/app-debug.apk`
   - 릴리스: `app/build/outputs/apk/release/app-release-unsigned.apk`
5. 릴리스 APK는 서명되지 않은 상태이므로, `apksigner` 또는 Android Studio를 사용해 개인 키로 서명한 뒤 배포합니다.

> **참고**: 위 명령어를 실행할 때는 Gradle이 의존성을 다운로드할 수 있도록 인터넷 연결이 필요합니다. 본 환경은 외부 저장소 접근이 제한되어 있어 APK를 미리 제공하지 못합니다.

## 권한
- `android.permission.RECORD_AUDIO`: 음성 인식을 위해 필요합니다.
