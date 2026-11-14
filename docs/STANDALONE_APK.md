# بناء لعبة Android مستقلة من STORYFORGE

يمكن تحويل مشروع قصة واحد إلى APK مستقل؛ يظهر على الهاتف باسم اللعبة وأيقونتها، ويفتح اللعبة مباشرة دون الحاجة إلى استيراد ملف داخل Player.

## الحالة الحالية: ما هو جاهز وما الذي يحتاج إعدادًا؟

### داخل تطبيق Android — كود العميل موجود

شاشة **مركز بناء الألعاب** مرتبطة بالفعل بطبقة `CloudBuildApi`، وتنفذ منطق العميل التالي:

- تسجيل الدخول عبر OIDC Authorization Code + PKCE، دون Client Secret داخل التطبيق.
- إنشاء ملف `.storygame` للمشروع المحدد داخل مساحة التطبيق المؤقتة.
- طلب رابط رفع قصير العمر، ثم رفع الملف مباشرة إلى تخزين خاص عبر HTTPS.
- إرسال طلب بناء، وحفظ رقم المهمة وحالتها محليًا.
- تحديث حالة المهمة وطلب رابط تنزيل مؤقت للـArtifact، ثم تسليمه إلى Android Download Manager.
- الاحتفاظ برمز الوصول في الذاكرة أثناء الجلسة فقط؛ لا يُحفظ في Preferences ولا يُرسل إلى Download Manager.

### Backend — الكود موجود لكنه غير منشور

يوجد أساس الخدمة في `cloud-build-service/`، ويضم Vercel Functions وواجهات API وقاعدة PostgreSQL والتحقق من OIDC وGitHub App ورفع الملفات إلى Cloudflare R2.

**الخدمة غير منشورة وغير مفعلة حاليًا.** لذلك وجود شاشة العميل والكود لا يعني أن البناء السحابي يعمل مباشرة بعد تثبيت التطبيق. يلزم نشر Backend وإعداد مزود الهوية وقاعدة البيانات وتطبيق GitHub وسلة R2 والمتغيرات السرية. لا توجد خدمة إنتاجية جاهزة يمكن للتطبيق الاتصال بها افتراضيًا.

## إعداد Backend قبل استخدام البناء السحابي

1. أنشئ مشروع Vercel مرتبطًا بالمستودع `omegathor3-afk/STORYFORGE`، واجعل Root Directory هو `cloud-build-service`، مع Node.js 20 أو أحدث.
2. جهّز PostgreSQL ونفّذ `cloud-build-service/db/schema.sql` مرة واحدة.
3. أنشئ مزود OIDC يدعم Authorization Code + PKCE (S256) وتطبيقًا Native/Public بلا Client Secret.
4. سجّل Redirect URI التالي كما هو: `com.storyforge.app:/oauth2redirect`.
5. أنشئ GitHub App محدودًا بمستودع STORYFORGE فقط، وبصلاحيات Actions read/write وContents read. لا تستخدم Personal Access Token داخل التطبيق.
6. أنشئ Cloudflare R2 bucket خاصًا، ومفتاح API محدودًا بهذه السلة، واضبط سياسة حذف دورية لمجلد `storygames/`.
7. أضف متغيرات Vercel التالية من لوحة التحكم، لا إلى Git:

| المتغير | الغرض |
|---|---|
| `DATABASE_URL` | اتصال PostgreSQL |
| `DATABASE_SSL` | اختياري؛ TLS verification مفعّل افتراضيًا |
| `OIDC_ISSUER` | Issuer الموثوق |
| `OIDC_AUDIENCE` | Audience الخاص بالـAPI |
| `OIDC_JWKS_URL` | رابط مفاتيح JWKS |
| `GITHUB_APP_ID` | معرّف GitHub App |
| `GITHUB_APP_PRIVATE_KEY` | المفتاح الخاص بالتطبيق |
| `GITHUB_APP_INSTALLATION_ID` | Installation ID للمستودع |
| `STORYGAME_ALLOWED_HOSTS` | قائمة النطاقات المسموح تنزيل ملفات القصص منها؛ ابدأ بنطاق R2 فقط |
| `R2_ACCOUNT_ID` | حساب Cloudflare |
| `R2_ACCESS_KEY_ID` | مفتاح R2 |
| `R2_SECRET_ACCESS_KEY` | سر R2 |
| `R2_BUCKET_NAME` | اسم السلة الخاصة |
| `STORYFORGE_BUILD_REF` | الفرع الذي يحتوي Workflow البناء؛ الافتراضي الحالي `feature/cloud-build-center` |
| `GITHUB_REPOSITORY_OWNER` | اختياري؛ الافتراضي `omegathor3-afk` |
| `GITHUB_REPOSITORY_NAME` | اختياري؛ الافتراضي `STORYFORGE` |

8. شغّل `npm install` ثم `npm run typecheck` من مجلد `cloud-build-service`، وبعدها انشر المشروع على Vercel.
9. من شاشة **مركز بناء الألعاب** أدخل Base URL لخدمة Vercel، وOIDC Issuer وClient ID وAPI Audience، ثم احفظ الإعدادات وسجّل الدخول.

تستخدم الخدمة المسارات التالية:

- `POST /api/uploads`: إنشاء روابط PUT/GET موقعة قصيرة العمر لملف `.storygame`.
- `POST /api/build-jobs`: إنشاء مهمة بناء وإرسال GitHub Actions workflow dispatch.
- `GET /api/build-jobs/:id`: قراءة حالة المهمة للمستخدم صاحبها.
- `GET /api/build-jobs/:id/artifact`: إعادة توجيه مؤقتة إلى ملف النتائج بعد التحقق من ملكية المهمة.

**لا تضع أسرار GitHub أو R2 أو توقيع APK في تطبيق Android أو المستودع.** كل المفاتيح التشغيلية تظل على الخادم.

## البناء اليدوي حتى نشر الخدمة

يمكنك استخدام GitHub Actions دون Backend إذا كان لديك رابط HTTPS مباشر لملف `.storygame` يمكن للـRunner تنزيله دون تسجيل دخول.

1. افتح [مستودع STORYFORGE](https://github.com/omegathor3-afk/STORYFORGE).
2. افتح Actions ثم **Build Standalone Story APK** واضغط Run workflow.
3. أدخل القيم التالية:

| الحقل | المطلوب |
|---|---|
| `storygame_url` | رابط HTTPS مباشر لحزمة `.storygame` |
| `game_title` | الاسم الظاهر أسفل أيقونة التطبيق؛ اختياري |
| `application_id` | معرّف فريد مثل `com.yourstudio.mygame`؛ اختياري |
| `icon_url` | رابط HTTPS مباشر لأيقونة PNG مربعة؛ اختياري |
| `version_name` | رقم مثل `1.0.0` |
| `version_code` | رقم موجب، وزده مع كل تحديث |
| `build_type` | `debug` للتجربة أو `release` لنسخة موقعة |

4. انتظر نجاح Workflow، ثم نزّل Artifact باسم يبدأ بـ `STORYFORGE-GAME-`.
5. فك ضغط ZIP وثبّت APK على جهاز Android.

### توقيع Release الحقيقي

- Debug لا يحتاج مفتاح توقيع إنتاجيًا.
- Release يتطلب أسرار GitHub Actions الأربعة: `ANDROID_KEYSTORE_BASE64` و`ANDROID_STORE_PASSWORD` و`ANDROID_KEY_ALIAS` و`ANDROID_KEY_PASSWORD`.
- استخدم مفتاحك الخاص الذي تحتفظ بنسخة احتياطية منه، ولا تضعه في التطبيق أو المستودع.
- مفتاح CI المؤقت يُنشأ ويُتلف داخل بيئة الاختبار للتحقق فقط؛ **ليس مفتاحًا صالحًا لتوقيع إصدار إنتاجي أو تحديث لعبة حقيقية**.

## قواعد التحديث

- احتفظ بنفس `application_id` عند تحديث اللعبة نفسها.
- زد `version_code` مع كل إصدار.
- استخدم مفتاح التوقيع نفسه لتحديثات Release خارج Google Play.
- استخدم PNG مربعة صغيرة للأيقونة.
- لكل لعبة تريد تثبيتها بجانب غيرها، استخدم Application ID مختلفًا.

## ما الذي يتحقق منه Workflow؟

- هوية الحزمة واسم التطبيق والإصدار.
- وجود `assets/default.storygame` داخل APK.
- سلامة ZIP الداخلي ووجود `game-manifest.json`.
- صحة توقيع APK عبر `apksigner`.
- SHA-256 وملف BUILD-INFO داخل Artifact.

