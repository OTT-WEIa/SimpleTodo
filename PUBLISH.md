# 发布到 GitHub 的步骤（仓库已经在本地准备好了，只差最后一步）

我在这台机器上只能做到"建好本地仓库并提交"，**没法替你登录 GitHub**：
这台机器没有装 GitHub CLI（`gh`），也没有任何 GitHub 凭据 / SSH 密钥，
而且登录你的账号、处理你的 token 这种事应该由你自己来。

下面任选一种，都能在两分钟内上线。

## 方式一：GitHub Desktop（最省事，不用命令行）

1. 打开 GitHub Desktop → `File` → `Add local repository…`
2. 选择这个目录：`D:\AI\SimpleTodo`
3. 右上角点 `Publish repository`
4. 想公开就**取消勾选** `Keep this code private` → `Publish`

完成后它会直接给你仓库地址。

## 方式二：命令行（推荐，两条命令）

先在网页上新建一个**空仓库**（不要勾选 Add README / .gitignore / license，否则会冲突）：

- 打开 <https://github.com/new>
- Repository name 填 `SimpleTodo`
- 描述可以填：`干点啥 —— 极简安卓待办清单 + 桌面小组件（无依赖 / 零权限 / 不联网）`
- 选 Public → `Create repository`

然后在本机执行（把 `你的用户名` 换成你的 GitHub 用户名）：

```bash
cd /d/AI/SimpleTodo
git remote add origin https://github.com/你的用户名/SimpleTodo.git
git push -u origin main
```

第一次推送会要账号和密码：**用户名填 GitHub 用户名，密码填 Personal Access Token**（不是登录密码）。
Token 在 GitHub 网页：`Settings → Developer settings → Personal access tokens → Tokens (classic) → Generate new token`，
勾上 `repo` 权限即可，生成后只显示一次，复制出来当密码用。

## 方式三：网页上传（不想装任何工具）

在新建的空仓库页面点 `uploading an existing file`，把 `SimpleTodo` 目录里的内容拖进去，
**注意不要拖 `local.properties`、`build/`、`.gradle/`、`SimpleTodo-v3.3.apk`**（这些是本机产物，已经在 .gitignore 里排除了）。

---

## 建议填的仓库信息（开源仓库的门面）

| 项目 | 建议值 |
| --- | --- |
| Repository name | `SimpleTodo` |
| Description | 干点啥 —— 极简安卓待办清单 + 桌面小组件（无依赖 / 零权限 / 不联网） |
| Topics | `android` `java` `todo` `todo-list` `widget` `appwidget` `no-dependencies` `chinese` |
| License | MIT（`.gitignore` 和 `LICENSE` 已经放在仓库里了，创建后 GitHub 会自动识别） |

## 想放 apk 给大家直接下载

GitHub 的 `Releases` 页面可以附二进制文件（仓库里的 `*.apk` 会被 .gitignore 排除，这是正常做法）：

1. 仓库页面右侧 `Releases` → `Draft a new release`
2. Tag 填 `v3.3`，Title 填 `干点啥 v3.3`
3. 把 `SimpleTodo-v3.3.apk` 拖到下方的附件区 → `Publish release`

之后 README 里的"Releases 页面附了 apk"那条就对上了。
