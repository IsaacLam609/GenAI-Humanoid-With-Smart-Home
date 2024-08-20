package com.ubtrobot.mini.speech.framework.demo;

import android.os.AsyncTask;
import android.util.Log;

import com.ubtrobot.mini.speech.framework.demo.MainActivity.FileUploadTask;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class PcmToWavConverter {
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL = 1;
    private static final int BITS_PER_SAMPLE = 16; //16 bits not 8 bits
    private static final String AUDIO_DIR = "/data/user/0/com.ubtrobot.mini.speech.framework.demo/files";
    private static final String AUDIO_FILE_NAME = "audiofile.wav";

    public static void convertPcmToWav(byte[] pcmData) {
        File wavFile = new File(AUDIO_DIR, AUDIO_FILE_NAME);
        try (FileOutputStream fos = new FileOutputStream(wavFile, false)) {
            writeWavHeader(fos, pcmData.length);
            fos.write(pcmData);
            fos.flush();
            fos.close();
            Log.i("Pcm To Wav Converter", "Pcm to Wav conversion completed");

            //upload file to flask server (laptop) in background

            //Wifi: IC Mesh
//            AsyncTask<String, Void, Boolean> task = new FileUploadTask().execute(wavFile.getPath(), "http://192.168.4.164:5000/upload");
            //Wifi: W402e
            AsyncTask<String, Void, Boolean> task = new FileUploadTask().execute(wavFile.getPath(), "http://192.168.49.196:5000/upload");


        } catch (IOException e) {
            Log.e("Pcm To Wav Converter", e.getMessage());
        }
    }

    private static void writeWavHeader(FileOutputStream fos, int audioLen) throws IOException {
        int blockAlign = (PcmToWavConverter.BITS_PER_SAMPLE / 8) * PcmToWavConverter.CHANNEL;
        int byteRate = PcmToWavConverter.SAMPLE_RATE * blockAlign;

        fos.write(new byte[] {'R', 'I', 'F', 'F'});
        writeInt(fos, 36 + audioLen); // ChunkSize
        fos.write(new byte[] {'W', 'A', 'V', 'E'});
        fos.write(new byte[] {'f', 'm', 't', ' '});
        writeInt(fos, 16); // Subchunk1Size
        writeShort(fos, (short) 1); // AudioFormat (1 = PCM)
        writeShort(fos, (short) PcmToWavConverter.CHANNEL);
        writeInt(fos, PcmToWavConverter.SAMPLE_RATE);
        writeInt(fos, byteRate);
        writeShort(fos, (short) blockAlign);
        writeShort(fos, (short) PcmToWavConverter.BITS_PER_SAMPLE);
        fos.write(new byte[] {'d', 'a', 't', 'a'});
        writeInt(fos, audioLen);
    }

    private static void writeInt(FileOutputStream fos, int value) throws IOException {
        fos.write((byte) (value & 0xFF));
        fos.write((byte) ((value >> 8) & 0xFF));
        fos.write((byte) ((value >> 16) & 0xFF));
        fos.write((byte) ((value >> 24) & 0xFF));
    }

    private static void writeShort(FileOutputStream fos, short value) throws IOException {
        fos.write((byte) (value & 0xFF));
        fos.write((byte) ((value >> 8) & 0xFF));
    }

}