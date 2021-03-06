package net.dean.jraw.testinguser

import kotlin.platform.platformStatic
import net.dean.jraw.RedditClient
import net.dean.jraw.Version
import java.util.Scanner
import java.security.SecureRandom
import java.math.BigInteger
import java.util.Random
import net.dean.jraw.http.Credentials
import net.dean.jraw.managers.AccountManager
import net.dean.jraw.AppType
import java.io.File
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.dean.jraw.managers.MultiRedditManager
import net.dean.jraw.models.Captcha
import net.dean.jraw.ApiException

/**
 * This class will create a Reddit user and set up everything you need to start testing with JRAW. See
 * <a href="https://github.com/thatJavaNerd/JRAW/issues/25">here</a> for a more detailed explanation of what this class
 * will do.
 *
 * When dealing with terminal output, the line length will be a maximum of 80 characters.
 */
public class CreateTestingUser {
    private val secureRandom = SecureRandom()
    private val weakRandom = Random()
    private val s = Scanner(System.`in`)
    private val reddit = RedditClient("Testing-User-Creator for JRAW v" + Version.get().formatted())

    class object {
        public platformStatic fun main(args: Array<String>) {
            if (args.size < 1)
                throw IllegalArgumentException("First argument should be the JSON configuration file")

            CreateTestingUser().`do`(File(args[0]))
        }
    }

    /**
     * Main logic of the script. Provides a high-level overview of the script's processes
     */
    fun `do`(jsonConfig: File) {
        val (username, password) = registerUser()
        val (id, secret) = createOAuth2App()
        val creds = Credentials.script(username, password, id, secret)
        storeData(creds, jsonConfig)
        createSubreddit()
        createMutlireddit()
        submitFirstPost()
    }

    /**
     * Retrieves a new captcha from reddit and prompts for the user to enter its answer.
     */
    fun promptForCaptcha(): Pair<Captcha, String> {
        val captcha = reddit.getNewCaptcha()
        val attempt = getInput(s, "Try captcha at \"${captcha.getImageUrl()}\"")
        return captcha to attempt
    }

    /**
     * Attempts to register a user. If an ApiException is encountered, it will try again until a user has been
     * successfully registered.
     *
     * Returns a Pair of username to password
     */
    fun registerUser(): Pair<String, String> {
        ////// STEP 1
        // "Take a username, password, password verification, optional email, and a captcha attempt and register a new user."

        val username: String
        val password: String

        println("Registering a new user")
        while (true) {
            // Keep looping until the user enters valid information
            username = getInput(s, "Enter a username", getUniqueUsername(reddit), filter = object : InputFilter {
                override fun check(str: String): String = if (reddit.isUsernameAvailable(str)) "" else "Username already taken"
            })
            password = getInput(s, "Enter a password", getSecureRandom(), filter = object : InputFilter {
                override fun check(str: String): String {
                    if (str.length == 0) return "Password cannot be empty"
                    return ""
                }
            })
            val email = getInput(s, "Enter an email (leave blank for none)")
            val (captcha, captchaAttempt) = promptForCaptcha()

            try {
                reddit.register(username, password, email, captcha, captchaAttempt)
            } catch (e: ApiException) {
                System.err.println("Failed to ceate a new user: ${e.getMessage()}")
                continue
            }
            // Bad practice to echo the password back in plain text?
            println("Successfully registered user '$username' with password '$password'")
            return username to password
        }
    }

    /**
     * Creates an OAuth2 app and returns a pair of the client ID to the client secret
     */
    fun createOAuth2App(): Pair<String, String> {
        ////// STEP 2
        // "Create a OAuth2 app with type 'script' (AccountManager.createOrUpdateApp())"
        val mgr = AccountManager(reddit)
        val url = "https://github.com/thatJavaNerd/JRAW"
        val name = "JRAW-testing-app"
        mgr.createOrUpdateApp(null, name, AppType.SCRIPT, "Created to test OAuth2 features in JRAW", url, url)
        println("Login with your new account and navigate to \"https://www.reddit.com/prefs/apps\"")
        println("Then find the client ID and secret of the '$name' app")
        println("See https://github.com/reddit/reddit/wiki/OAuth2#getting-started for help.")

        val id = getInput(s, "Enter $name's client ID")
        val secret = getInput(s, "Enter $name's client secret")
        return id to secret
    }

    /**
     * Writes the given Credentials object to the given file in JSON format
     */
    fun storeData(creds: Credentials, jsonConfig: File) {
        ////// STEP 3
        // "Write the username, password, client id, and client secret to credentials.json using Jackson"
        val data = mapOf("username" to creds.getUsername(),
                "password" to creds.getPassword(),
                "client_id" to creds.getClientId(),
                "client_secret" to creds.getClientSecret())
        jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValue(jsonConfig, data)
        println("Your testing user's credentials can be found at ${jsonConfig.getAbsolutePath()}")
    }

    /**
     * Creates a subreddit for the user
     */
    fun createSubreddit() {
        ////// STEP 4
        // "Create a subreddit"
        // TODO Actually implement this
        println("Create a subreddit with your new account at https://www.reddit.com/subreddits/create")
    }

    /**
     * Creates a multireddit whose name is "jraw"
     */
    fun createMutlireddit() {
        ////// STEP 5
        // "Create a multireddit whose name is _not_ jraw_testing"
        val mgr = MultiRedditManager(reddit)
        val name = "jraw"
        println("Creating multireddit '$name'")
        val multi = mgr.createOrUpdate(name, listOf("programming", "java", "git", "lolphp"), true)
        mgr.updateDescription(name, "This mutlireddit was created using JRAW because you had no other multireddits. " +
                                    "Feel free to delete this multireddit, but tests will fail if you don't have at " +
                                    "least one multireddit with at least one subreddit in it")
        println("Your new multireddit can be accessed at")
        println("https://reddit.com${multi.getPath()}")
    }

    /**
     * Submits a selfpost to /r/jraw_testing2
     */
    fun submitFirstPost() {
        ////// STEP 6
        // "Submit a selfpost to /r/jraw_testing2"
        val subreddit = "jraw_testing2"
        println("Submitting your first selfpost to /r/$subreddit")
        val mgr = AccountManager(reddit)
        // We're going to need to do a captcha since this user has under 10 karma
        val (captcha, attempt) = promptForCaptcha()
        val submission = mgr.submit(AccountManager.SubmissionBuilder("my [f]irst post, be gentle",
                                                                     subreddit,
                                                                     "New testing user"), captcha, attempt)
        println("Submitted your first post to /r/jraw_testing2: ${submission.getShortURL()}")
    }

    /**
     * Tries to retrieve a unique username. The output will be a random number between 0 and 999,999 appended to the end
     * of the string "jrawTestUser". For example, "jrawTestUser625741".
     */
    fun getUniqueUsername(reddit: RedditClient): String {
        var username: String

        do {
            username = "jrawTestUser" + weakRandom.nextInt(1000000)
        } while (!reddit.isUsernameAvailable(username))

        return username
    }

    /**
     * Generates a cryptographically secure alphanumeric string 26 characters long
     */
    fun getSecureRandom(): String = BigInteger(130, secureRandom).toString(32)
}

/**
 * Gets input from the user. If 'prompt' is not empty then it will be printed before the scanner is read. The user will
 * be continued to be prompted until the InputFilter returns an empty string (meaning that there was no error in the
 * input).
 */
fun getInput(s: Scanner, prompt: String, defaultValue: String = "", filter: InputFilter = object: InputFilter {
    override fun check(str: String): String {
        return "";
    }}): String {


    var input: String
    var error: String
    val usedPrompt = if (defaultValue.isEmpty()) prompt + ": " else "$prompt ($defaultValue): "

    do {
        // Check if a prompt was given
        if (!prompt.isEmpty())
            // Print the calculated prompt instead of the given prompt
            println(usedPrompt)

        print("> ")
        input = s.nextLine()

        // Use the default value if no input is given
        if (input.trim().isEmpty() && defaultValue.isNotEmpty()) {
            return defaultValue
        }

        // Check for an error in the input
        error = filter.check(input)
        // An empty string means that there was an error
        if (error.isNotEmpty()) {
            System.err.println(error)
        }

    } while (error.isNotEmpty())

    return input
}

trait InputFilter {
    /**
     * Checks the given input string for errors. A return value of an empty string means that there was no error.
     */
    fun check(str: String): String
}
