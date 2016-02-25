package org.gilmour.ffmpeg.util;

import org.bytedeco.javacpp.Loader;

import static org.bytedeco.javacpp.avcodec.avcodec_register_all;
import static org.bytedeco.javacpp.avdevice.avdevice_register_all;
import static org.bytedeco.javacpp.avformat.av_register_all;
import static org.bytedeco.javacpp.avformat.avformat_network_init;

/**
 * Created by alicana on 25.02.2016 at 10:47.
 */
public class FFmpegLibLoader {

    private static FFmpegLibLoader.Exception loadingException = null;

    public static void tryLoad() throws FFmpegLibLoader.Exception {

	if (loadingException != null) {
	    throw loadingException;
	} else {
	    try {

		Loader.load(org.bytedeco.javacpp.avutil.class);
		Loader.load(org.bytedeco.javacpp.swresample.class);
		Loader.load(org.bytedeco.javacpp.avcodec.class);
		Loader.load(org.bytedeco.javacpp.avformat.class);
		Loader.load(org.bytedeco.javacpp.swscale.class);

		// Register all formats and codecs
		avcodec_register_all();
		av_register_all();
		avformat_network_init();

		Loader.load(org.bytedeco.javacpp.avdevice.class);
		avdevice_register_all();

	    } catch (Throwable t) {
		throw loadingException = new Exception("Failed to load " + FFmpegLibLoader.class, t);
	    }
	}
    }

    public static class Exception extends java.lang.Exception {

	public Exception(String message) {

	    super(message);
	}

	public Exception(String message, Throwable cause) {

	    super(message, cause);
	}
    }


}
