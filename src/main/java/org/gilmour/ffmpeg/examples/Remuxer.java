package org.gilmour.ffmpeg.examples;

import org.bytedeco.javacpp.PointerPointer;
import org.gilmour.ffmpeg.util.FFmpegLibLoader;

import static org.bytedeco.javacpp.avcodec.AVCodec;
import static org.bytedeco.javacpp.avcodec.AVPacket;
import static org.bytedeco.javacpp.avcodec.CODEC_FLAG_GLOBAL_HEADER;
import static org.bytedeco.javacpp.avcodec.av_free_packet;
import static org.bytedeco.javacpp.avcodec.avcodec_copy_context;
import static org.bytedeco.javacpp.avcodec.avcodec_find_encoder;
import static org.bytedeco.javacpp.avcodec.avcodec_open2;
import static org.bytedeco.javacpp.avformat.AVFMT_GLOBALHEADER;
import static org.bytedeco.javacpp.avformat.AVFMT_NOFILE;
import static org.bytedeco.javacpp.avformat.AVFormatContext;
import static org.bytedeco.javacpp.avformat.AVIOContext;
import static org.bytedeco.javacpp.avformat.AVIO_FLAG_WRITE;
import static org.bytedeco.javacpp.avformat.AVInputFormat;
import static org.bytedeco.javacpp.avformat.AVOutputFormat;
import static org.bytedeco.javacpp.avformat.AVStream;
import static org.bytedeco.javacpp.avformat.av_dump_format;
import static org.bytedeco.javacpp.avformat.av_interleaved_write_frame;
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
import static org.bytedeco.javacpp.avutil.AVMEDIA_TYPE_AUDIO;
import static org.bytedeco.javacpp.avutil.AVMEDIA_TYPE_VIDEO;
import static org.bytedeco.javacpp.avutil.AV_NOPTS_VALUE;
import static org.bytedeco.javacpp.avutil.av_dict_free;
import static org.bytedeco.javacpp.avutil.av_dict_set;
import static org.bytedeco.javacpp.avutil.av_free;
import static org.bytedeco.javacpp.avutil.av_rescale_q;

/**
 * Created by gilmour on 24.02.2016.
 *
 * @see <a href="https://ffmpeg.org/doxygen/trunk/doc_2examples_2remuxing_8c-example.html">remuxing.c</a>
 * <p/>
 * <p/>
 * <p/>
 * This class basically, (demux) reads a media file and split it into chunks of data, then, (mux) takes encoded data int the form of AVPackets
 * and writes into files or other output bytestreams in the specified container format.
 * <p/>
 * (from <a href="https://ffmpeg.org/doxygen/2.8/group__libavf.html">I/O and Muxing/Demuxing Library</a>)
 */
public class Remuxer {

    // input-output file
    //    public static final String in_filename = "/home/alicana/Videos/HD-720p.mp4";

    //    public static final String out_filename = "/home/alicana/Videos/HD-720p_copy.mp4";

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

    // ====================== Variables ======================

    private AVOutputFormat ofmt = null;

    private AVFormatContext ifmt_ctx = null;

    private AVFormatContext ofmt_ctx = null;

    private AVPacket pkt = new AVPacket();

    private int ret;

    int vid_st_idx = -1, aud_st_idx = -1;

    // ====================== Initiate ======================

    public void openMedia(String in_filename) throws Exception {

	ifmt_ctx = new AVFormatContext(null);

	// Open an input stream and read the header
	// TODO The stream must be closed with avformat_close_input().
	AVInputFormat f = new AVInputFormat(null);
	AVDictionary options = new AVDictionary(null);
	if ((ret = avformat_open_input(ifmt_ctx, in_filename, f, options)) < 0) {
	    av_dict_set(options, "pixel_format", null, 0);
	    if ((ret = avformat_open_input(ifmt_ctx, in_filename, f, options)) < 0) {
		throw new Exception("avformat_open_input() error " + ret + ": Could not open input \"" + in_filename + "\". (Has setFormat() been called?)");
	    }
	}

	av_dict_free(options);

	// Read packets of a media file to get stream information
	if ((ret = avformat_find_stream_info(ifmt_ctx, (PointerPointer) null)) < 0) {
	    throw new Exception("avformat_find_stream_info() error:\tFailed to retrieve input stream information");
	}

	// Print detailed information about the format
	av_dump_format(ifmt_ctx, 0, in_filename, 0);

	for (int i = 0; i < ifmt_ctx.nb_streams(); i++) {
	    AVStream in_stream = ifmt_ctx.streams(i);
	    if (vid_st_idx == -1 && in_stream.codec().codec_type() == AVMEDIA_TYPE_VIDEO) {

		vid_st_idx = i;

	    } else if (aud_st_idx == -1 && in_stream.codec().codec_type() == AVMEDIA_TYPE_AUDIO) {

		aud_st_idx = i;

	    }
	}

    }

    public void initOutput(String out_filename, String outputFormat) throws Exception {

	ofmt = null;

	ofmt_ctx = new AVFormatContext(null);

	// Allocate an AVFormatContext for an output format.
	// TODO avformat_free_context() can be used to free the context and everything allocated by the framework within it.
	if (avformat_alloc_output_context2(ofmt_ctx, null, outputFormat, out_filename) < 0) {
	    ret = AVERROR_UNKNOWN;
	    throw new Exception("avformat_alloc_output_context2() error:\tCould not create output context\n");
	}

	ofmt = ofmt_ctx.oformat();
	ofmt_ctx.filename().putString(out_filename);

	if (vid_st_idx != -1 && ifmt_ctx.streams(vid_st_idx).codec().codec_type() == AVMEDIA_TYPE_VIDEO) {

	    AVCodec codec = avcodec_find_encoder(ifmt_ctx.streams(vid_st_idx).codec().codec_id());
	    if (codec == null) {
		throw new Exception(
				"avcodec_find_decoder() error:\tUnsupported video format or codec not found: " + ifmt_ctx.streams(vid_st_idx).codec().codec_id()
						+ ".");
	    }

	    AVDictionary options = new AVDictionary(null);

	    // Open video codec
	    if ((ret = avcodec_open2(ifmt_ctx.streams(vid_st_idx).codec(), codec, options)) < 0) {
		throw new Exception("avcodec_open2() error:\t" + ret + ": Could not open video codec.");
	    }
	    av_dict_free(options);

	    // Hack to correct wrong frame rates that seem to be generated by some codecs
	    if (ifmt_ctx.streams(vid_st_idx).codec().time_base().num() > 1000 && ifmt_ctx.streams(vid_st_idx).codec().time_base().den() == 1) {
		ifmt_ctx.streams(vid_st_idx).codec().time_base().den(1000);
	    }

	    AVStream out_stream = avformat_new_stream(ofmt_ctx, ifmt_ctx.streams(vid_st_idx).codec().codec());

	    if (out_stream == null) {
		ret = AVERROR_UNKNOWN;
		throw new Exception("avformat_new_stream() error:\tFailed allocating output video stream\n");
	    }

	    ret = avcodec_copy_context(out_stream.codec(), ifmt_ctx.streams(vid_st_idx).codec());
	    if (ret < 0) {
		throw new Exception("avcodec_copy_context() error:\tFailed to copy context from input video to output video stream codec context\n");
	    }

	    int m_fps = 25; // default value

	    if (ifmt_ctx.streams(vid_st_idx).r_frame_rate().num() != AV_NOPTS_VALUE && ifmt_ctx.streams(vid_st_idx).r_frame_rate().den() != 0) {
		m_fps = (ifmt_ctx.streams(vid_st_idx).r_frame_rate().num()) / (ifmt_ctx.streams(vid_st_idx).r_frame_rate().den());
	    }

	    out_stream.codec().codec_id(ifmt_ctx.streams(vid_st_idx).codec().codec_id());
	    //	    	    out_stream.codec().codec_tag(ifmt_ctx.streams(vid_st_idx).codec().codec_tag());
	    out_stream.codec().codec_tag(0);

	    out_stream.sample_aspect_ratio().den(out_stream.codec().sample_aspect_ratio().den());
	    out_stream.sample_aspect_ratio().num(ifmt_ctx.streams(vid_st_idx).codec().sample_aspect_ratio().num());
	    out_stream.codec().time_base().num(1);
	    out_stream.codec().time_base().den(m_fps * (ifmt_ctx.streams(vid_st_idx).codec().ticks_per_frame()));

	    out_stream.time_base().num(1);
	    out_stream.time_base().den(1000);
	    out_stream.r_frame_rate().num(m_fps);
	    out_stream.r_frame_rate().den(1);
	    out_stream.avg_frame_rate().den(1);
	    out_stream.avg_frame_rate().num(m_fps);

	    if ((ofmt_ctx.oformat().flags() & AVFMT_GLOBALHEADER) != 0) {
		out_stream.codec().flags(out_stream.codec().flags() | CODEC_FLAG_GLOBAL_HEADER);
	    }

	}

	if (aud_st_idx != -1 && ifmt_ctx.streams(aud_st_idx).codec().codec_type() == AVMEDIA_TYPE_AUDIO) {

	    AVCodec aud_codec = avcodec_find_encoder(ifmt_ctx.streams(aud_st_idx).codec().codec_id());

	    if (aud_codec == null) {
		throw new Exception(
				"avcodec_find_decoder() error:\tUnsupported audio format or codec not found: " + ifmt_ctx.streams(aud_st_idx).codec().codec_id()
						+ ".");
	    }

	    AVStream out_aud_stream = avformat_new_stream(ofmt_ctx, ifmt_ctx.streams(aud_st_idx).codec().codec());

	    if (out_aud_stream == null) {
		ret = AVERROR_UNKNOWN;
		throw new Exception("avformat_new_stream() error:\tFailed allocating output audio stream\n");
	    }

	    ret = avcodec_copy_context(out_aud_stream.codec(), ifmt_ctx.streams(aud_st_idx).codec());
	    if (ret < 0) {
		throw new Exception("avcodec_copy_context() error:\tFailed to copy context from input audio to output audio stream codec context\n");
	    }

	    out_aud_stream.codec().codec_id(ifmt_ctx.streams(aud_st_idx).codec().codec_id());
	    out_aud_stream.codec().codec_tag(0);
	    out_aud_stream.pts(ifmt_ctx.streams(aud_st_idx).pts());
	    out_aud_stream.duration(ifmt_ctx.streams(aud_st_idx).duration());
	    out_aud_stream.time_base().num(ifmt_ctx.streams(aud_st_idx).time_base().num());
	    out_aud_stream.time_base().den(ifmt_ctx.streams(aud_st_idx).time_base().den());

	    if ((ofmt_ctx.oformat().flags() & AVFMT_GLOBALHEADER) != 0) {
		out_aud_stream.codec().flags(out_aud_stream.codec().flags() | CODEC_FLAG_GLOBAL_HEADER);
	    }

	}

	// Print detailed information about the format
	av_dump_format(ofmt_ctx, 0, out_filename, 1);

	// Create and initialize a AVIOContext for accessing the resource indicated by url.
	if ((ofmt.flags() & AVFMT_NOFILE) == 0) {
	    AVIOContext pb = new AVIOContext(null);
	    ret = avio_open(pb, out_filename, AVIO_FLAG_WRITE);
	    if (ret < 0) {
		throw new Exception("avio_open() error:\tCould not open output file '%s'" + out_filename);
	    }
	    ofmt_ctx.pb(pb);
	}

	AVDictionary out_opts = new AVDictionary(null);
	ret = avformat_write_header(ofmt_ctx, out_opts);
	if (ret < 0) {
	    throw new Exception("avformat_write_header() error:\tError occurred when opening output file\n");
	}
	av_dict_free(out_opts);
    }

    public int recordAVPacket() throws Exception {

	AVStream in_stream, out_stream;

	// Return the next frame of a stream.
	if ((ret = av_read_frame(ifmt_ctx, pkt)) < 0) {
	    return ret;
	}

	in_stream = ifmt_ctx.streams(pkt.stream_index());
	out_stream = ofmt_ctx.streams(pkt.stream_index());

	if (ofmt_ctx.streams(vid_st_idx) == null) {
	    throw new Exception("No video output stream");
	}

	if (in_stream.codec().codec_type() == AVMEDIA_TYPE_VIDEO || in_stream.codec().codec_type() == AVMEDIA_TYPE_AUDIO) {

	    /* copy packet */
	    //	     pkt.pts(av_rescale_q_rnd(pkt.pts(), in_stream.codec().time_base(), out_stream.codec().time_base(), AV_ROUND_NEAR_INF | AV_ROUND_PASS_MINMAX));
	    //	     pkt.dts(av_rescale_q_rnd(pkt.dts(), in_stream.codec().time_base(), out_stream.codec().time_base(), AV_ROUND_NEAR_INF | AV_ROUND_PASS_MINMAX));

	    pkt.dts(AV_NOPTS_VALUE);
	    pkt.pts(AV_NOPTS_VALUE);

	    pkt.duration((int) av_rescale_q(pkt.duration(), in_stream.codec().time_base(), out_stream.codec().time_base()));
	    pkt.pos(-1);

	    synchronized (ofmt_ctx) {
		ret = av_interleaved_write_frame(ofmt_ctx, pkt);
		if (ret < 0) {
		    throw new Exception("av_write_frame() error:\tWhile muxing packet\n");
		}
	    }

	    av_free_packet(pkt);

	}

	return 0;
    }

    public void stop() throws Exception {

	if (ofmt_ctx != null) {
	    av_write_frame(ofmt_ctx, null);

	    // Write the stream trailer to an output media file and free the file private data.
	    av_write_trailer(ofmt_ctx);
	}

	// close input
	if (ifmt_ctx != null && !ifmt_ctx.isNull()) {
	    // Close an opened input AVFormatContext.
	    // Free it and all its contents and set *s to NULL.
	    avformat_close_input(ifmt_ctx);
	    ifmt_ctx = null;
	}

	// close output
	if (!ofmt_ctx.isNull()) {
	    if ((ofmt_ctx.oformat().flags() & AVFMT_NOFILE) == 0) {
		/* close the output file */
		avio_close(ofmt_ctx.pb());
	    }

	    /* free the streams */
	    int nb_streams = ofmt_ctx.nb_streams();
	    for (int i = 0; i < nb_streams; i++) {
		av_free(ofmt_ctx.streams(i).codec());
		av_free(ofmt_ctx.streams(i));
	    }

            /* free metadata */
	    if (ofmt_ctx.metadata() != null) {
		av_dict_free(ofmt_ctx.metadata());
		ofmt_ctx.metadata(null);
	    }

            /* free the stream */
	    av_free(ofmt_ctx);
	    ofmt_ctx = null;

	}

	avformat_free_context(ofmt_ctx);

	if (ret < 0 && ret != AVERROR_EOF) {
	    throw new Exception("Error occurred: %s\n");
	}

    }

    public void release() {

	synchronized (org.bytedeco.javacpp.avcodec.class) {

	}
    }

    @Override
    protected void finalize() throws Throwable {

	super.finalize();
	release();
    }

    // ====================== Main ======================

    public static void main(String[] args) throws Exception {

	Remuxer rmx = new Remuxer();

	rmx.openMedia("/home/alicana/Videos/demo_videos/SampleVideo_640x360_10mb.mp4");
	rmx.initOutput("/home/alicana/Videos/records/copy_SampleVideo_640x360_10mb.mp4", "mp4");
	while (rmx.recordAVPacket() == 0)
	    ;
	rmx.stop();

    }

}
