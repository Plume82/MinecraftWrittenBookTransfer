# WrittenBookTransfer —— Minecraft 成书工具箱 | WrittenBookTransfer - Minecraft Written Book Toolkit

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java Version](https://img.shields.io/badge/Java-25-orange.svg)](https://www.oracle.com/java/)

> 从存档提取、智能排版到一键输入游戏，一站式管理 Minecraft 成书（Written Book）。确保已安装 [Java 25+](https://www.oracle.com/java/)，运行 `WrittenBookTransferGUI.java` 即可进入图形化界面。  
> *From archive extraction and intelligent typesetting to one-click in-game input, an all-in-one solution for managing Minecraft written books. Ensure [Java 25+](https://www.oracle.com/java/) is installed, then run `WrittenBookTransferGUI.java` to launch the graphical interface.*

**WrittenBookTransfer** 是一款面向 Minecraft 玩家和服务器管理员的综合性成书管理工具。它提供了图形化的书籍数据库管理、AI 驱动的价值过滤、智能去重、以及从存档直接提取成书并自动排版输入游戏的全流程功能。  
*WrittenBookTransfer is a comprehensive written book management tool for Minecraft players and server administrators. It offers a graphical book database manager, AI-powered value filtering, intelligent deduplication, and an end-to-end workflow from extracting books directly from world saves to automatically typesetting and inputting them into the game.*

---

## ✨ 主要功能 | Main Features

### 🗃️ 书籍数据库管理（GUI）| Book Database Management (GUI)
- **文件夹即数据库**：直接选择包含 `.mcfunction` 书籍文件的文件夹，自动解析并展示书籍元数据（书名、作者、世代、页数、字数）。  
  *Folder as Database: Simply select a folder containing `.mcfunction` book files. Metadata (title, author, generation, pages, word count) is automatically parsed and displayed.*
- **双表格浏览**：左侧显示根目录书籍，右侧显示子目录书籍，支持拖拽移动书籍到任意子文件夹。  
  *Dual-table view: The left table shows books in the root directory, while the right table shows books in subdirectories. Drag-and-drop is fully supported for moving books between folders.*
- **全文搜索**：可按标题/作者搜索，或全文检索书籍内容。  
  *Full-text search: Search by title/author or perform a full-text search within book contents.*
- **回收站支持**：删除的书籍移入回收站，可随时恢复或彻底删除。  
  *Recycle Bin support: Deleted books are moved to the recycle bin and can be restored or permanently deleted at any time.*
- **右键菜单**：打开、删除、移动、翻译等快捷操作。  
  *Context menu: Quick actions such as open, delete, move, and translate.*

### 🤖 AI 价值过滤 | AI Value Filtering
- 调用 **DeepSeek API** 对全库书籍进行智能评分（内容完整性、语言表达、趣味性、留存价值）。  
  *Utilizes the **DeepSeek API** to intelligently score all books in the library (content completeness, language expression, originality/interest, and retention value).*
- 自动筛选低价值书籍（评分 < 60），生成详细报告并支持一键移入回收站。  
  *Automatically filters low-value books (score < 60), generates a detailed report, and supports one-click moving to the recycle bin.*
- 支持 API 并发调用，大幅提升处理速度。  
  *Supports concurrent API calls, greatly improving processing speed.*

### 📦 存档提取成书（独立工具）| Archive Extraction (Standalone Tool)
- 从 Minecraft 存档的 `region` 文件夹中提取所有 `written_book` 物品（支持箱子、讲台、掉落物）。  
  *Extracts all `written_book` items from the `region` folder of a Minecraft world save (supports chests, barrels, lecterns, and item entities).*
- 多线程处理，支持暂停/继续、进度保存。  
  *Multi-threaded processing with pause/resume and progress saving.*
- 自动为每本成书生成 `.mcfunction` 命令文件，可直接在游戏中 `/function` 加载。  
  *Automatically generates `.mcfunction` command files for each book, ready to be loaded in-game with `/function`.*

### ✍️ 智能排版与传输 | Intelligent Typesetting & Transfer
- **Minecraft 风格预览**：模拟游戏内成书界面，实时查看分页效果。  
  *Minecraft-style preview: Simulates the in-game book interface for real-time page layout preview.*
- **智能分页**：自动识别章节标题（如“第一章”、“1.”），并交互式询问是否另起一页，支持自定义分页标记。  
  *Smart pagination: Automatically detects chapter headings (e.g., "Chapter 1", "1.") and interactively asks whether to start a new page. Custom page-break markers are supported.*
- **默认段落编排**：自动规范段落间距（段落间空一行，首尾不空行）。  
  *Default paragraph formatting: Automatically normalizes paragraph spacing (one blank line between paragraphs, no leading/trailing blank lines).*
- **一键传输**：将排版好的书籍内容自动粘贴输入到 Minecraft 游戏中（通过模拟键盘操作）。其操作思路借鉴了 [Minecraft-Book-Printer](https://github.com/LanYangYang321/Minecraft-Book-Printer) 的设计。  
  *One-click transfer: Automatically pastes the typeset book content into Minecraft (via keyboard simulation). The approach is inspired by [Minecraft-Book-Printer](https://github.com/LanYangYang321/Minecraft-Book-Printer).*

### 📄 辅助工具 | Auxiliary Tools
- **书籍详情页**：展示原文及 AI 翻译结果，支持移动、删除、AI 评分。  
  *Book detail page: Displays original text and AI translation results, with support for move, delete, and AI scoring.*
- **数据库预处理（去重）**：在同一作者内，按内容完全相同的书籍只保留最原始版本，移除重复副本。  
  *Database preprocessing (deduplication): For each author, only the most original version of identical books is kept; duplicate copies are removed.*
- **排版参数可调**：每页行数、最大行宽、鼠标偏移等均可自定义。  
  *Adjustable typesetting parameters: Lines per page, maximum line width, mouse offset, etc., are all customizable.*

---

## 🛠️ 技术栈 | Tech Stack

| 依赖 / Dependency | 用途 / Purpose |
| :--- | :--- |
| **Java 25** | 核心语言 / Core Language |
| **Swing** | 图形界面框架 / GUI Framework |
| **Maven** | 项目构建与依赖管理 / Build & Dependency Management |
| **OkHttp** | HTTP 客户端（调用 DeepSeek API） / HTTP Client (DeepSeek API) |
| **Jackson / Gson** | JSON 数据处理 / JSON Processing |
| **Apache POI** | Word 报告生成 / Word Report Generation |
| **H2 Database** | 嵌入式数据库，存储书籍元数据 / Embedded Database for Metadata |
| **JNativeHook** | 全局键盘监听（传输快捷键） / Global Keyboard Listener (Transfer Hotkeys) |

---

### 环境要求 | Requirements
- **Java 25** 或更高版本 / *Java 25 or later*
- **Maven 3.6+**（若需手动构建） / *Maven 3.6+ (if building manually)*
