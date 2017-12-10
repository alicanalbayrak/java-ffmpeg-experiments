package org.gilmour.ffmpeg.examples;

import static org.bytedeco.javacpp.avcodec.av_packet_unref;
import static org.bytedeco.javacpp.avcodec.avcodec_parameters_copy;
import static org.bytedeco.javacpp.avformat.AVFMT_NOFILE;
import static org.bytedeco.javacpp.avformat.AVIO_FLAG_WRITE;
import static org.bytedeco.javacpp.avformat.av_dump_format;
import static org.bytedeco.javacpp.avformat.av_interleaved_write_frame;
import static org.bytedeco.javacpp.avformat.av_read_frame;
import static org.bytedeco.javacpp.avformat.av_write_trailer;
import static org.bytedeco.javacpp.avformat.avformat_alloc_output_context2;
import static org.bytedeco.javacpp.avformat.avformat_close_input;
import static org.bytedeco.javacpp.avformat.avformat_find_stream_info;
import static org.bytedeco.javacpp.avformat.avformat_free_context;
import static org.bytedeco.javacpp.avformat.avformat_new_stream;
import static org.bytedeco.javacpp.avformat.avformat_open_input;
import static org.bytedeco.javacpp.avformat.avformat_write_header;
import static org.bytedeco.javacpp.avformat.avio_closep;
import static org.bytedeco.javacpp.avformat.avio_open;
import static org.bytedeco.javacpp.avutil.AVMEDIA_TYPE_AUDIO;
import static org.bytedeco.javacpp.avutil.AVMEDIA_TYPE_SUBTITLE;
import static org.bytedeco.javacpp.avutil.AVMEDIA_TYPE_VIDEO;
import static org.bytedeco.javacpp.avutil.AV_ROUND_NEAR_INF;
import static org.bytedeco.javacpp.avutil.AV_ROUND_PASS_MINMAX;
import static org.bytedeco.javacpp.avutil.av_dict_free;
import static org.bytedeco.javacpp.avutil.av_rescale_q;
import static org.bytedeco.javacpp.avutil.av_rescale_q_rnd;

import java.util.logging.Logger;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.javacpp.avcodec.AVCodecParameters;
import org.bytedeco.javacpp.avcodec.AVPacket;
import org.bytedeco.javacpp.avformat.AVFormatContext;
import org.bytedeco.javacpp.avformat.AVIOContext;
import org.bytedeco.javacpp.avformat.AVInputFormat;
import org.bytedeco.javacpp.avformat.AVOutputFormat;
import org.bytedeco.javacpp.avformat.AVStream;
import org.bytedeco.javacpp.avutil.AVDictionary;
import org.gilmour.ffmpeg.util.FFmpegLibLoader;

/**
 * Created by gilmour on 10.12.2017.
 * Java implementation of ffmpeg stream copy
 * @see <a href="https://github.com/FFmpeg/FFmpeg/blob/4a946aca7cf3c03d232953852405577e85f4da71/doc/examples/remuxing.c">remuxing.c</a>
 */
public class Remuxing {

  private static final Logger LOGGER = Logger.getLogger(Remuxing.class.getName());

  static {
    try {
      FFmpegLibLoader.tryLoad();
    } catch (FFmpegLibLoader.Exception e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) throws Exception {

    AVOutputFormat ofmt = null;
    AVFormatContext ifmt_ctx = new AVFormatContext(null);
    AVFormatContext ofmt_ctx = new AVFormatContext(null);
    AVPacket pkt = new AVPacket();
    int ret;
    int i;
    int[] stream_mapping;
    int stream_index = 0;
    int stream_mapping_size = 0;

    String in_filename = "/home/gilmour/Videos/SampleVideo_720x480_10mb.mp4";
    String out_filename = "/home/gilmour/Videos/records/copy_SampleVideo_640x360_10mb.mp4";

    AVInputFormat avInputFormat = new AVInputFormat(null);
    AVDictionary avDictionary = new AVDictionary(null);
    if ((ret = avformat_open_input(ifmt_ctx, in_filename, avInputFormat, avDictionary)) < 0) {
      LOGGER.severe(String.format("Could not open input file %s", in_filename));
    }

    av_dict_free(avDictionary);

    // Read packets of a media file to get stream information
    if ((ret = avformat_find_stream_info(ifmt_ctx, (PointerPointer) null)) < 0) {
      throw new Exception(
          "avformat_find_stream_info() error:\tFailed to retrieve input stream information");
    }

    av_dump_format(ifmt_ctx, 0, in_filename, 0);

    if ((ret = avformat_alloc_output_context2(ofmt_ctx, null, null, out_filename)) < 0) {
      throw new Exception(
          "avformat_alloc_output_context2() error:\tCould not create output context\n");
    }

    stream_mapping_size = ifmt_ctx.nb_streams();
    stream_mapping = new int[stream_mapping_size];

    ofmt = ofmt_ctx.oformat();

    for (int stream_idx = 0; stream_idx < stream_mapping_size; stream_idx++) {
      AVStream out_stream;
      AVStream in_stream = ifmt_ctx.streams(stream_idx);

      AVCodecParameters in_codedpar = in_stream.codecpar();

      if (in_codedpar.codec_type() != AVMEDIA_TYPE_AUDIO &&
          in_codedpar.codec_type() != AVMEDIA_TYPE_VIDEO &&
          in_codedpar.codec_type() != AVMEDIA_TYPE_SUBTITLE) {
        stream_mapping[stream_idx] = -1;
        continue;
      }

      stream_mapping[stream_idx] = stream_index++;

      out_stream = avformat_new_stream(ofmt_ctx, null);

      ret = avcodec_parameters_copy(out_stream.codecpar(), in_codedpar);
      if (ret < 0) {
        LOGGER.severe("Failed to copy codec parameters");
      }
      out_stream.codecpar().codec_tag(0);
    }

    av_dump_format(ofmt_ctx, 0, out_filename, 1);

    if ((ofmt.flags() & AVFMT_NOFILE) == 0) {
      AVIOContext pb = new AVIOContext(null);
      ret = avio_open(pb, out_filename, AVIO_FLAG_WRITE);
      if (ret < 0) {
        throw new Exception("avio_open() error:\tCould not open output file '%s'" + out_filename);
      }
      ofmt_ctx.pb(pb);
    }

    AVDictionary avOutDict = new AVDictionary(null);
    ret = avformat_write_header(ofmt_ctx, avOutDict);
    if (ret < 0) {
      LOGGER.severe("Error occurred when opening output file");
    }

    for (; ; ) {
      AVStream in_stream, out_stream;
      // Return the next frame of a stream.
      if ((ret = av_read_frame(ifmt_ctx, pkt)) < 0) {
        break;
      }

      in_stream = ifmt_ctx.streams(pkt.stream_index());
      if (pkt.stream_index() >= stream_mapping_size ||
          stream_mapping[pkt.stream_index()] < 0) {
        av_packet_unref(pkt);
        continue;
      }

      pkt.stream_index(stream_mapping[pkt.stream_index()]);
      out_stream = ofmt_ctx.streams(pkt.stream_index());
      // log_packet

      pkt.pts(av_rescale_q_rnd(pkt.pts(), in_stream.time_base(), out_stream.time_base(),
          AV_ROUND_NEAR_INF | AV_ROUND_PASS_MINMAX));
      pkt.dts(av_rescale_q_rnd(pkt.dts(), in_stream.time_base(), out_stream.time_base(),
          AV_ROUND_NEAR_INF | AV_ROUND_PASS_MINMAX));
      pkt.duration(av_rescale_q(pkt.duration(), in_stream.time_base(), out_stream.time_base()));
      pkt.pos(-1);

      synchronized (ofmt_ctx) {
        ret = av_interleaved_write_frame(ofmt_ctx, pkt);
        if (ret < 0) {
          throw new Exception("av_write_frame() error:\tWhile muxing packet\n");
        }
      }

      av_packet_unref(pkt);

    }

    av_write_trailer(ofmt_ctx);

    avformat_close_input(ifmt_ctx);

    if (!ofmt_ctx.isNull() && (ofmt.flags() & AVFMT_NOFILE) == 0) {
      avio_closep(ofmt_ctx.pb());
    }

    avformat_free_context(ofmt_ctx);


  }


}
