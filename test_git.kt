import java.io.File
fun main() {
    val engine = com.example.verb.terminal.TerminalCommandEngine
    val res = engine.executeCommand("git status", File("."))
    println("Result: ${res.output}")
}
