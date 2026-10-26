# Third-party software and data

The app's original code is MIT licensed. Third-party software retains its own license.

- MapLibre Native Android 13.5.3: BSD-2-Clause. Copyright 2021 MapLibre contributors, 2018–2021 MapTiler.com, 2014–2020 Mapbox. The full license is bundled in `app/src/main/assets/licenses/maplibre.txt`. Source: https://github.com/maplibre/maplibre-native/tree/android-v13.5.3 .
- WGS84 / GCJ02 / BD09 coordinate transformations are adapted from gcoord 0.3.2, MIT, Copyright 2018-present Jiulong Hu. Full notice: `app/src/main/assets/licenses/gcoord.txt`. Source: https://github.com/hujiulong/gcoord .
- Kotlin, kotlinx.coroutines, AndroidX / Jetpack Compose and OkHttp: Apache License 2.0. Full license: `app/src/main/assets/licenses/apache-2.0.txt`. See their respective published artifacts and source repositories for copyright notices and transitive dependencies.
- JUnit 4.13.2 and org.json are used only by development tests and are not shipped as application dependencies.

Map tiles and forecasts are separate service/data authorizations, not covered by this repository's license. The app displays Tianditu attribution and requires a user-provided authorized key. Weather is supplied by Open-Meteo; the free endpoint is for personal non-commercial use and requires attribution. No application key or private signing key is distributed in the repository.
