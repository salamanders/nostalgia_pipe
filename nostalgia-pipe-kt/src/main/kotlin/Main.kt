import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.terminal.Terminal
import com.nostalgiapipe.config.Config
import com.nostalgiapipe.orchestrator.Orchestrator
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>) = runBlocking {
    val terminal = Terminal()

    if (args.isEmpty()) {
        terminal.println(red("Error: No command provided. Use 'submit' or 'finalize'."))
        return@runBlocking
    }

    try {
        val config = Config.load()
        val orchestrator = Orchestrator(config)

        when (args[0].lowercase()) {
            "submit" -> orchestrator.submit()
            "finalize" -> orchestrator.finalize()
            else -> terminal.println(red("Error: Unknown command '${args[0]}'. Use 'submit' or 'finalize'."))
        }
    } catch (e: IllegalStateException) {
        logger.error(e) { "Configuration Error" }
        terminal.println(red("Configuration Error: ${e.message}"))
    } catch (e: Exception) {
        logger.error(e) { "An unexpected error occurred" }
        terminal.println(red("An unexpected error occurred: ${e.message}"))
    }
}
