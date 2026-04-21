import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.Scanner;

/**
 * 独立小程序：接收从 Word 粘贴的多行文本，移除所有换行符拼接为一行，
 * 输出到终端，并自动将结果复制到系统剪贴板。
 * 
 * 使用方法：
 *   1. 运行程序
 *   2. 从 Word 复制文本，粘贴到终端（可包含多行）
 *   3. 输入完成后按回车，程序将显示拼接结果并自动复制到剪贴板
 *   4. 输入空行直接回车退出程序
 */
public class ClipboardWordFlattener {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in, "UTF-8");
        System.out.println("========================================");
        System.out.println("  Word 文本拼接工具（自动去换行+复制到剪贴板）");
        System.out.println("========================================");
        System.out.println("请从 Word 复制文本，粘贴到此处（可包含多行），");
        System.out.println("完成后按回车键（若需退出，直接输入空行并回车）。");
        System.out.println("----------------------------------------");

        while (true) {
            System.out.print("\n> 请粘贴文本（完成后按回车）: ");
            String line = scanner.nextLine();
            
            // 如果直接输入空行，则退出
            if (line.isEmpty()) {
                System.out.println("已退出。");
                break;
            }

            // 由于终端粘贴多行时，Scanner.nextLine() 只会读取到第一行，
            // 我们需要继续读取后续行，直到遇到空行（用户按两次回车表示结束）。
            // 但通常从 Word 粘贴时，所有文本会一次性进入输入流，包含换行符。
            // 这里采用更健壮的方式：持续读取直到遇到空行，同时处理可能已读完的情况。
            StringBuilder pastedText = new StringBuilder(line);
            if (line.contains("\n") || line.contains("\r")) {
                // 如果第一行已经包含换行符（某些终端会一次性读入全部），我们直接处理整个字符串
                // 无需额外读取
            } else {
                // 逐行读取剩余内容，直到遇到空行
                System.out.println("（继续粘贴剩余行，完成后输入空行并回车）");
                while (true) {
                    String nextLine = scanner.nextLine();
                    if (nextLine.isEmpty()) {
                        break;
                    }
                    pastedText.append("\n").append(nextLine);
                }
            }

            String original = pastedText.toString();
            
            // 移除所有换行符（\r\n, \r, \n）
            String flattened = original.replace("\r\n", "")
                                       .replace("\r", "")
                                       .replace("\n", "");
            
            // 输出到终端
            System.out.println("\n========== 拼接结果 ==========");
            System.out.println(flattened);
            System.out.println("================================");
            
            // 复制到系统剪贴板
            copyToClipboard(flattened);
            System.out.println("✅ 已自动复制到剪贴板。");
            
            // 询问是否继续
            System.out.print("\n是否继续处理下一段？(y/N): ");
            String choice = scanner.nextLine().trim().toLowerCase();
            if (!choice.startsWith("y")) {
                System.out.println("程序结束。");
                break;
            }
        }
        scanner.close();
    }

    /**
     * 将文本复制到系统剪贴板
     */
    private static void copyToClipboard(String text) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        StringSelection selection = new StringSelection(text);
        clipboard.setContents(selection, null);
    }
}