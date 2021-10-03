package org.sunhacks;

import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.CombinedAudio;
import net.dv8tion.jda.api.entities.VoiceChannel;

import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

public class AudioReceiveListener implements AudioReceiveHandler {
    private List<byte[]> receivedBytes = new ArrayList<>();
    private double volume;
    private static final int MAX_TIME = 30 * 1000 / 20; // 30 seconds

    public AudioReceiveListener(double volume, VoiceChannel voiceChannel) {
        this.volume = volume;
    }

    @Override
    public boolean canReceiveCombined() {
        return true;
    }

    @Override
    public boolean canReceiveUser() {
        return false;
    }

    @Override
    public void handleCombinedAudio(CombinedAudio combinedAudio) {
        try {
            if (receivedBytes.size() > MAX_TIME) // records only past MAX_TIME seconds
            {
                receivedBytes.remove(0);
            }
            receivedBytes.add(combinedAudio.getAudioData(volume));
        } catch (OutOfMemoryError e) {
            // close connection
        }
    }

    private void getWavFile(File outFile, byte[] decodedData) throws IOException {
        AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(decodedData), AudioReceiveHandler.OUTPUT_FORMAT,
                decodedData.length), AudioFileFormat.Type.WAVE, outFile);

    }

    static String getAlphaNumericString(int n) {
        // chose a Character random from this String
        String AlphaNumericString = "ABCDEFGHIJKLMNOPQRSTUVWXYZ" + "0123456789" + "abcdefghijklmnopqrstuvxyz";

        // create StringBuffer size of AlphaNumericString
        StringBuilder sb = new StringBuilder(n);

        for (int i = 0; i < n; i++) {

            // generate a random number between
            // 0 to AlphaNumericString variable length
            int index = (int) (AlphaNumericString.length() * Math.random());

            // add Character one by one in end of sb
            sb.append(AlphaNumericString.charAt(index));
        }

        return sb.toString();
    }

    // Method that outputs the recorded audio data (byte array) to a WAVE audio file
    public File createFile(int seconds) {
        int packetCount = (seconds * 1000) / 20; // number of milliseconds you want to record / data sent every 20ms
        File file;
        try {
            int size = 0;
            List<byte[]> packets = new ArrayList<>();
            int lastPacket = receivedBytes.size() - packetCount < 0 ? 0 : receivedBytes.size() - packetCount;

            // add audio data (receivedBytes) for specified length of time (packetCount)
            for (int x = receivedBytes.size(); x > lastPacket; x--) {
                packets.add(0, receivedBytes.get(x - 1));
            }

            for (byte[] bs : packets) {
                size += bs.length;
            }
            byte[] decodedData = new byte[size];
            int i = 0;
            for (byte[] bs : packets) {
                for (int j = 0; j < bs.length; j++) {
                    decodedData[i++] = bs[j];
                }
            }

            file = new File(getAlphaNumericString(16) + ".wav");
            file.createNewFile();

            getWavFile(file, decodedData);
        } catch (IOException | OutOfMemoryError e) {
            file = null;
            e.printStackTrace();
        }

        return file;
    }
}
