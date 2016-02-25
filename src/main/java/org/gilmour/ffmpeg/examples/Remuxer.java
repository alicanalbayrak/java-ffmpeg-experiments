package org.gilmour.ffmpeg.examples;

import org.bytedeco.javacpp.PointerPointer;
import org.gilmour.ffmpeg.util.FFmpegLibLoader;

import static org.bytedeco.javacpp.avcodec.AVPacket;
import static org.bytedeco.javacpp.avcodec.CODEC_FLAG_GLOBAL_HEADER;
import static org.bytedeco.javacpp.avcodec.av_free_packet;
import static org.bytedeco.javacpp.avcodec.avcodec_copy_context;
import static org.bytedeco.javacpp.avformat.AVFMT_GLOBALHEADER;
import static org.bytedeco.javacpp.avformat.AVFMT_NOFILE;
import static org.bytedeco.javacpp.avformat.AVFormatContext;
import static org.bytedeco.javacpp.avformat.AVIOContext;
import static org.bytedeco.javacpp.avformat.AVIO_FLAG_WRITE;
import static org.bytedeco.javacpp.avformat.AVInputFormat;
import static org.bytedeco.javacpp.avformat.AVOutputFormat;
import static org.bytedeco.javacpp.avformat.AVStream;
import static org.bytedeco.javacpp.avformat.av_dump_format;
import static org.bytedeco.javacpp.avformat.av_read_frame;
import static org.bytedeco.javacpp.avformat.av_write_frame;
import static org.bytedeco.javacpp.avformat.av_write_trailer;
import static org.bytedeco.javacpp.avformat.avformat_alloc_output_context2;
import static org.bytedeco.javacpp.avformat.avformat_close_input;
import static org.bytedeco.javacpp.avformat.avformat_find_stream_info;
import static org.bytedeco.javacpp.avformat.avformat_free_context;
import static org.bytedeco.javacpp.avformat.avformat_new_stream;
import static org.bytedeco.javacpp.avformat.avformat_open_input;
import static org.bytedeco.javacpp.avformat.avformat_write_header;
import static org.bytedeco.javacpp.avformat.avio_close;
import static org.bytedeco.javacpp.avformat.avio_open;
import static org.bytedeco.javacpp.avutil.AVDictionary;
import static org.bytedeco.javacpp.avutil.AVERROR_EOF;
import static org.bytedeco.javacpp.avutil.AVERROR_UNKNOWN;
import static org.bytedeco.javacpp.avutil.AV_ROUND_NEAR_INF;
import static org.bytedeco.javacpp.avutil.AV_ROUND_PASS_MINMAX;
import static org.bytedeco.javacpp.avutil.av_dict_free;
import static org.bytedeco.javacpp.avutil.av_dict_set;
import static org.bytedeco.javacpp.avutil.av_rescale_q;
import static org.bytedeco.javacpp.avutil.av_rescale_q_rnd;

/**
 * Created by gilmour on 24.02.2016.
 *
 * @see <a href="https://ffmpeg.org/doxygen/trunk/doc_2examples_2remuxing_8c-example.html">remuxing.c</a>
 */
public class Remuxer {

    // input-output file
    public static final String in_filename = "";

    public static final String out_filename = "";

    // ====================== Load ffmpeg libraries ======================

    static {
	try {
	    FFmpegLibLoader.tryLoad();
	} catch (FFmpegLibLoader.Exception e) {
	    e.printStackTrace();
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

    // ====================== Main ======================

    public static void main(String[] args) throws Exception {

	AVOutputFormat ofmt = null;
	AVFormatContext ifmt_ctx = new AVFormatContext(null);
	AVFormatContext ofmt_ctx = new AVFormatContext(null);
	AVPacket pkt = new AVPacket();
	int ret, i;

	AVInputFormat f = new AVInputFormat(null);
	AVDictionary options = new AVDictionary(null);
	if ((ret = avformat_open_input(ifmt_ctx, in_filename, f, options)) < 0) {
	    av_dict_set(options, "pixel_format", null, 0);
	    if ((ret = avformat_open_input(ifmt_ctx, in_filename, f, options)) < 0) {
		throw new Remuxer.Exception(
				"avformat_open_input() error " + ret + ": Could not open input \"" + in_filename + "\". (Has setFormat() been called?)");
	    }
	}

	av_dict_free(options);

	if ((ret = avformat_find_stream_info(ifmt_ctx, (PointerPointer) null)) < 0) {
	    throw new Remuxer.Exception("Failed to retrieve input stream information");
	}

	av_dump_format(ifmt_ctx, 0, in_filename, 0);

	if (avformat_alloc_output_context2(ofmt_ctx, null, "mp4", out_filename) < 0) {
	    ret = AVERROR_UNKNOWN;
	    throw new Remuxer.Exception("Could not create output context\n");
	}

	ofmt = ofmt_ctx.oformat();

	for (i = 0; i < ifmt_ctx.nb_streams(); i++) {

	    AVStream in_stream = ifmt_ctx.streams(i);

	    AVStream out_stream = avformat_new_stream(ofmt_ctx, in_stream.codec().codec());

	    if (out_stream == null) {
		ret = AVERROR_UNKNOWN;
		throw new Remuxer.Exception("Failed allocating output stream\n");
	    }

	    ret = avcodec_copy_context(out_stream.codec(), in_stream.codec());
	    if (ret < 0) {
		throw new Remuxer.Exception("Failed to copy context from input to output stream codec context\n");
	    }
	    out_stream.codec().codec_tag(0);

	    if ((ofmt_ctx.oformat().flags() & AVFMT_GLOBALHEADER) != 0) {
		out_stream.codec().flags(out_stream.codec().flags() | CODEC_FLAG_GLOBAL_HEADER);
	    }
	}

	av_dump_format(ofmt_ctx, 0, out_filename, 1);

	if ((ofmt.flags() & AVFMT_NOFILE) == 0) {
	    AVIOContext pb = new AVIOContext(null);
	    ret = avio_open(pb, out_filename, AVIO_FLAG_WRITE);
	    if (ret < 0) {
		throw new Remuxer.Exception("Could not open output file '%s'" + out_filename);
	    }
	    ofmt_ctx.pb(pb);
	}

	AVDictionary out_opts = new AVDictionary(null);
	ret = avformat_write_header(ofmt_ctx, out_opts);
	if (ret < 0) {
	    throw new Remuxer.Exception("Error occurred when opening output file\n");
	}
	av_dict_free(out_opts);

	long t1 = System.currentTimeMillis();
	while (true) {
	    AVStream in_stream, out_stream;

	    if ((System.currentTimeMillis() - t1) > 5000)
		break;

	    ret = av_read_frame(ifmt_ctx, pkt);
	    if (ret < 0)
		break;

	    in_stream = ifmt_ctx.streams(pkt.stream_index());
	    out_stream = ofmt_ctx.streams(pkt.stream_index());

        /* copy packet */
	    pkt.pts(av_rescale_q_rnd(pkt.pts(), in_stream.time_base(), out_stream.time_base(), AV_ROUND_NEAR_INF | AV_ROUND_PASS_MINMAX));
	    pkt.dts(av_rescale_q_rnd(pkt.dts(), in_stream.time_base(), out_stream.time_base(), AV_ROUND_NEAR_INF | AV_ROUND_PASS_MINMAX));
	    pkt.duration((int) av_rescale_q(pkt.duration(), in_stream.time_base(), out_stream.time_base()));
	    pkt.pos(-1);
	    //log_packet(ofmt_ctx, &pkt, "out");

	    ret = av_write_frame(ofmt_ctx, pkt);
	    if (ret < 0) {
		throw new Remuxer.Exception("Error muxing packet\n");
	    }
	    av_free_packet(pkt);
	}

	av_write_trailer(ofmt_ctx);

	avformat_close_input(ifmt_ctx);

    /* close output */
	if (!ofmt_ctx.isNull()) {
	    if ((ofmt_ctx.oformat().flags() & AVFMT_NOFILE) == 0) {
		/* close the output file */
		avio_close(ofmt_ctx.pb());
	    }
	}
	avformat_free_context(ofmt_ctx);

	if (ret < 0 && ret != AVERROR_EOF) {
	    throw new Remuxer.Exception("Error occurred: %s\n");
	}

    }

}
