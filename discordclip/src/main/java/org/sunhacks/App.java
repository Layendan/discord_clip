package org.sunhacks;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.managers.AudioManager;

// Creating commands dependnecies
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.security.auth.login.LoginException;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.util.EnumSet;
import java.util.List;

/**
 * Hello world!
 *
 */
public class App extends ListenerAdapter {

    public static void main(String[] args) throws LoginException {
        JDA jda = JDABuilder.createDefault(System.getenv("DISCORD_TOKEN")) // slash commands don't need any intents
                .addEventListeners(new App()).build();

        System.out.println(jda.getInviteUrl(EnumSet.noneOf(Permission.class)));
        // These commands take up to an hour to be activated after
        // creation/update/delete
        // CommandListUpdateAction commands = jda.updateCommands();
        for (Command command : jda.retrieveCommands().complete()) {
            System.out.println(command.getName());
        }

        // Moderation commands with required options
        // commands.addCommands(new CommandData("join", "Joins the voice channel you are
        // connected in"),
        // new CommandData("clip", "Clip your voice chat").addOptions(
        // new OptionData(OptionType.INTEGER, "time", "The amount of time you want to
        // clip in seconds")),
        // new CommandData("deafen", "Deafen and Undeafen the bot if you want to keep
        // stuff private"));

        // Send the new set of commands to discord, this will override any existing
        // global commands with the new set provided here
        // commands.queue();
    }

    @Override
    public void onSlashCommand(SlashCommandEvent event) {
        // Only accept user commands
        if (event.getUser().isBot())
            return;
        // Only accept commands from guilds
        if (event.getGuild() == null)
            return;
        switch (event.getName()) {
            case "join":
                VoiceChannel voiceChannel = event.getMember().getVoiceState().getChannel();
                AudioManager audioManager = event.getGuild().getAudioManager();
                // User is not in a voice channel
                if (voiceChannel == null) {
                    event.reply("You are not connected to a voice channel!").setEphemeral(true).queue();
                    return;
                } else if (!event.getGuild().getSelfMember().hasPermission(voiceChannel, Permission.VOICE_CONNECT)) {
                    event.reply("I am not allowed to join voice channels").setEphemeral(true).queue();
                } else {
                    if (event.getGuild().getSelfMember().getVoiceState().getChannel() != null) {
                        audioManager.closeAudioConnection();
                        event.reply("Left").setEphemeral(true).queue();
                    } else {
                        voiceChannel.getBitrate();
                        audioManager.openAudioConnection(voiceChannel);
                        // initalize the audio reciever listener
                        audioManager.setReceivingHandler(new AudioReceiveListener(1, voiceChannel));
                        event.reply("Joined").queue();
                    }
                }
                break;
            case "clip":
                AudioReceiveHandler receiveHandler = event.getGuild().getAudioManager().getReceivingHandler();

                if (receiveHandler == null) {
                    event.reply("I am not connected to a voice channel").setEphemeral(true).queue();
                    return;
                }

                OptionMapping option = event.getOption("time");
                int time = 30;
                if (option != null) {
                    time = Integer.parseInt(option.getAsString());
                    if (time > 30 || time < 1) {
                        event.reply("Please enter a value between 1 and 30").setEphemeral(true).queue();
                        return;
                    }
                }
                File file = ((AudioReceiveListener) receiveHandler).createFile(time);
                event.reply("<@" + event.getUser().getId() + ">, Caught yo ass in 48KHz \\\\(￣︶￣*\\\\))").queue();
                event.getChannel().sendFile(file).queue((message) -> {
                    List<Attachment> attachments = message.getAttachments();
                    try {
                        event.getChannel()
                                .sendMessage(
                                        "__**Transcript:**__ \n> " + makeHttpPOSTRequest(attachments.get(0).getUrl()))
                                .queue();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    // use messageId here
                });

                break;
            case "deafen":
                if (event.getGuild().getAudioManager().getReceivingHandler() == null) {
                    event.reply("I am not connected to a voice chat").setEphemeral(true).queue();
                    return;
                }

                if (event.getGuild().getSelfMember().getVoiceState().isDeafened()) {
                    event.getGuild().getAudioManager().setSelfDeafened(false);
                    event.reply("Undeafened :thumbsup:").setEphemeral(true).queue();
                } else {
                    event.getGuild().getAudioManager().setSelfDeafened(true);
                    event.reply("Deafened :thumbsup:").setEphemeral(true).queue();
                }
                break;
            default:
                event.reply("I can't handle that command right now :(").setEphemeral(true).queue();
        } // end of switch
    } // end of method

    /**
     * This function uploads the audio to Assembly AI, get the ID, and use the ID to
     * call the function *makeHttpGETRequest* to get the transcript of the audio.
     * Notice, this function will automatically calls the *makeHttpGETRequest*
     * function.
     * 
     * @param inputLink the link of the audio
     * 
     */
    private String makeHttpPOSTRequest(String inputLink) throws IOException {
        URL url = new URL("https://api.assemblyai.com/v2/transcript");
        HttpURLConnection http = (HttpURLConnection) url.openConnection();
        http.setRequestMethod("POST");
        http.setDoOutput(true);
        http.setRequestProperty("authorization", System.getenv("ASSEMBLYAI_TOKEN"));
        http.setRequestProperty("content-type", "application/json");

        String data = "{\"audio_url\": \"" + inputLink + "\"}";

        byte[] out = data.getBytes(StandardCharsets.UTF_8);

        OutputStream stream = http.getOutputStream();
        stream.write(out);

        InputStream inputStream = http.getInputStream();

        System.out.println(http.getResponseCode() + " " + http.getResponseMessage());
        System.out.println(http.getContentType());
        // convert inputStream (lots of numbers) -> readable string (json in string
        // type)
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line + "\n");
        }
        br.close();

        JSONObject json = JSON.parseObject(sb.toString());
        String id = json.getString("id");

        http.disconnect();

        String text = makeHttpGETRequest(id);

        System.out.println("HTTPREQUEST String: " + text);

        return text;

    } // end of method

    /**
     * *This function will be automatically called from the function
     * "makeHttpPOSTRequest", you don't need to call this function*.<br/>
     * This function get the transcript from AssemblyAI of a audio file we uploaded
     * from the function *makeHttpPOSTRequest*.
     * 
     * @param id the ID from the json returned by the AssemblyAI identifying the
     *           audio file we uploaded. We need to use it to access the transcript.
     * @return the transcript of the audio
     */
    public String makeHttpGETRequest(String id) throws IOException {
        // first, get the id attribute from the String json

        URL url = new URL("https://api.assemblyai.com/v2/transcript/" + id);
        HttpURLConnection http = (HttpURLConnection) url.openConnection();
        http.setRequestProperty("authorization", System.getenv("ASSEMBLYAI_TOKEN"));
        http.setRequestProperty("content-type", "application/json");

        System.out.println(http.getResponseCode() + " " + http.getResponseMessage());

        BufferedReader br = new BufferedReader(new InputStreamReader(http.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line + "\n");
        }
        br.close();
        System.out.println(sb.toString());

        JSONObject json = JSON.parseObject(sb.toString());
        String status = json.getString("status");
        System.out.println(status);
        String text = "";
        if (status.equals("queued") || status.equals("processing")) {

            try {
                Thread.sleep(500); // 0.5 seconds to process
                text = makeHttpGETRequest(id);
                return text;
            } catch (InterruptedException e) {
                e.printStackTrace();
                return "There was an error with the request please try again later or contact the developer";
            }
        } else if (status.equals("completed")) {
            text = json.getString("text");
            http.disconnect();
            return text;
        } else if (status.equals("error")) {
            http.disconnect();
            return "There was an error with the request please try again later or contact the developer";
        }
        http.disconnect();

        return "There was an error with the request please try again later or contact the developer";
    }
} // end of class
