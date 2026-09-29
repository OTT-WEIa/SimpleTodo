# 发布与维护

## 仓库地址（已上线）

<https://github.com/OTT-WEIa/SimpleTodo> —— Public，MIT 协议，`main` 分支已推送。

## 日常维护：改完代码怎么更新上去

```bash
cd /d/AI/SimpleTodo
git add -A
git commit -m "说明这次改了什么"
git push
```

用 GitHub Desktop 也一样：写 commit message → `Commit to main` → `Push origin`。

> 本机没有保存 GitHub 凭据，如果 `git push` 弹窗要账号密码：
> **用户名填 GitHub 用户名，密码填 Personal Access Token**（不是登录密码）。
> Token 在网页 `Settings → Developer settings → Personal access tokens → Tokens (classic) → Generate new token`，
> 勾 `repo` 权限即可，生成后只显示一次。想一劳永逸就让弹窗记住凭据，之后直接 push。
> 更省事的办法：改完直接用 GitHub Desktop 提交并推送（它已经登录过了，不用再输 token）。

## 想放 apk 给大家直接下载

仓库里的 `*.apk` 会被 `.gitignore` 排除，这是正常做法；二进制放 Release 更合适：

1. 仓库页面右侧 `Releases` → `Draft a new release`
2. Tag 填 `v3.3`，Title 填 `干点啥 v3.3`
3. 把 `SimpleTodo-v3.3.apk` 拖到下方附件区 → `Publish release`

## 仓库门面（可选，建议补上）

仓库首页 `About` 右侧齿轮里可以填 `Topics`：
`android` `java` `todo` `todo-list` `widget` `appwidget` `no-dependencies` `chinese`

Description 已经填好：`干点啥 —— 极简安卓待办清单 + 桌面小组件（无依赖 / 零权限 / 不联网）`

## 换台机器继续开发

```bash
git clone https://github.com/OTT-WEIa/SimpleTodo.git
cd SimpleTodo
echo "sdk.dir=/你的/Android/Sdk/路径" > local.properties   # Windows 用正斜杠更省事
./gradlew assembleDebug                                   # Windows: gradlew.bat assembleDebug
```
