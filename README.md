# LocTime

あらかじめ設定した場所と時間で通知を表示するAndroidアプリです。

## 機能

- 場所を最大10件登録
- 1つの場所につき、時刻を最大5件設定
- 時刻設定に通知テキストを付加可能
- 端末再起動後もアラームを自動復元

## 場所の設定方法

| 方法 | 説明 |
|---|---|
| 住所入力 | 住所を入力して検索（`android.location.Geocoder` を使用） |
| 現在地取得 | GPSで現在地を自動入力 |
| 手動入力 | 緯度・経度を直接入力 |

## 動作フロー

時刻になったときにGPS位置情報を取得し、登録済みの場所の範囲内（半径Nm）であれば通知を表示します。
常時GPSを使用しないため、バッテリー消費を抑えられます。

## 技術構成

| 項目 | 内容 |
|---|---|
| 対象OS | Android 7.0 (API 24) 以上 |
| 言語 | Kotlin |
| UI | Jetpack Compose / Material3 |
| データベース | Room |
| 位置情報 | FusedLocationProviderClient (Google Play Services) |
| ジオコーディング | android.location.Geocoder（帰属表示不要） |
| ナビゲーション | Navigation Compose |

## ライセンス

MIT License
