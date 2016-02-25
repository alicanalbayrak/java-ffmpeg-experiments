extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libavutil/avutil.h>
}

int main(int argc, char* argv[])
{
    const char * kInputFileName = "f:/Projects/Temp/testFFMPEG2/test/test_in.avi";
    const char * kOutputFileName = "f:/Projects/Temp/testFFMPEG2/test/text_out.avi";
    const char * kOutputFileType = "avi";

    av_register_all();

    AVFormatContext * inCtx = NULL;
    int err = avformat_open_input(&inCtx, kInputFileName, NULL, NULL);
    if (err < 0)
        exit(1);

    err = av_find_stream_info(inCtx);
    if (err < 0)
        exit(1);


    int vs = -1;
    for (unsigned int s = 0; s < inCtx->nb_streams; ++s)
    {
        if (inCtx->streams[s] &&
            inCtx->streams[s]->codec &&
            inCtx->streams[s]->codec->codec_type == AVMEDIA_TYPE_VIDEO)
        {
            vs = s;
            break;
        }        
    }

    if (vs == -1)
        exit(1);

    AVOutputFormat * outFmt = av_guess_format(kOutputFileType, NULL, NULL);
    if (!outFmt)
        exit(1);

    AVFormatContext *outCtx = NULL;
    err = avformat_alloc_output_context2(&outCtx, outFmt, NULL, NULL);

    if (err < 0 || !outCtx)
        exit(1);

    AVStream * outStrm = av_new_stream(outCtx, 0);
    AVStream const * const inStrm = inCtx->streams[vs];
    AVCodec * codec = NULL;
    avcodec_get_context_defaults3(outStrm->codec, codec);
    outStrm->codec->thread_count = 1;

#if (LIBAVFORMAT_VERSION_MAJOR == 53)
    outStrm->stream_copy = 1;
#endif

    outStrm->codec->coder_type = AVMEDIA_TYPE_VIDEO;
    if(outCtx->oformat->flags & AVFMT_GLOBALHEADER) 
        outStrm->codec->flags |= CODEC_FLAG_GLOBAL_HEADER;

    outStrm->codec->sample_aspect_ratio = outStrm->sample_aspect_ratio = inStrm->sample_aspect_ratio; 

#if (LIBAVFORMAT_VERSION_MAJOR == 53)
    outCtx->timestamp = 0;
#endif

    err = avio_open(&outCtx->pb, kOutputFileName, AVIO_FLAG_WRITE);
    if (err < 0)
        exit(1);

#if (LIBAVFORMAT_VERSION_MAJOR == 53)
    AVFormatParameters params = {0};
    err = av_set_parameters(outCtx, &params);
    if (err < 0)
        exit(1);
#endif

    outStrm->disposition = inStrm->disposition;
    outStrm->codec->bits_per_raw_sample = inStrm->codec->bits_per_raw_sample;
    outStrm->codec->chroma_sample_location = inStrm->codec->chroma_sample_location;
    outStrm->codec->codec_id = inStrm->codec->codec_id;
    outStrm->codec->codec_type = inStrm->codec->codec_type;

    if (!outStrm->codec->codec_tag)
    {
        if (! outCtx->oformat->codec_tag
            || av_codec_get_id (outCtx->oformat->codec_tag, inStrm->codec->codec_tag) == outStrm->codec->codec_id
            || av_codec_get_tag(outCtx->oformat->codec_tag, inStrm->codec->codec_id) <= 0)
                    outStrm->codec->codec_tag = inStrm->codec->codec_tag;
    }

    outStrm->codec->bit_rate = inStrm->codec->bit_rate;
    outStrm->codec->rc_max_rate = inStrm->codec->rc_max_rate;
    outStrm->codec->rc_buffer_size = inStrm->codec->rc_buffer_size;

    const size_t extra_size_alloc = (inStrm->codec->extradata_size > 0) ?
                                    (inStrm->codec->extradata_size + FF_INPUT_BUFFER_PADDING_SIZE) :
                                     0;

    if (extra_size_alloc)
    {
        outStrm->codec->extradata = (uint8_t*)av_mallocz(extra_size_alloc);    
        memcpy( outStrm->codec->extradata, inStrm->codec->extradata, inStrm->codec->extradata_size);
    }
    outStrm->codec->extradata_size = inStrm->codec->extradata_size;

    AVRational input_time_base = inStrm->time_base;
    AVRational frameRate = {25, 1};
    if (inStrm->r_frame_rate.num && inStrm->r_frame_rate.den 
        && (1.0 * inStrm->r_frame_rate.num / inStrm->r_frame_rate.den < 1000.0))
    {
        frameRate.num = inStrm->r_frame_rate.num;
        frameRate.den = inStrm->r_frame_rate.den;
    }

    outStrm->r_frame_rate = frameRate;
    outStrm->codec->time_base = inStrm->codec->time_base;

    outStrm->codec->pix_fmt = inStrm->codec->pix_fmt;
    outStrm->codec->width =  inStrm->codec->width;
    outStrm->codec->height =  inStrm->codec->height;
    outStrm->codec->has_b_frames =  inStrm->codec->has_b_frames;
    if (! outStrm->codec->sample_aspect_ratio.num) {
        AVRational r0 = {0, 1};
        outStrm->codec->sample_aspect_ratio =
            outStrm->sample_aspect_ratio =
            inStrm->sample_aspect_ratio.num ? inStrm->sample_aspect_ratio :
            inStrm->codec->sample_aspect_ratio.num ?
            inStrm->codec->sample_aspect_ratio : r0;
    }
#if LIBAVFORMAT_VERSION_MAJOR == 53
    av_write_header(outFmtCtx);
#else
    avformat_write_header(outCtx, NULL);
#endif


    for (;;)
    {
        AVPacket packet = {0};
        av_init_packet(&packet);

        err = AVERROR(EAGAIN);
        while (AVERROR(EAGAIN) == err) 
            err = av_read_frame(inCtx, &packet);

        if (err < 0)
        {
            if (AVERROR_EOF != err && AVERROR(EIO) != err)
            {
                // error
                exit(1);            
            }
            else
            {
                // end of file
                break;
            }            
        }


        if (packet.stream_index == vs)
        {

            err = av_interleaved_write_frame(outCtx, &packet);
            if (err < 0)
                exit(1);
        }            

        av_free_packet(&packet);        

    }

    av_write_trailer(outCtx);
    if (!(outCtx->oformat->flags & AVFMT_NOFILE) && outCtx->pb)
        avio_close(outCtx->pb);

    avformat_free_context(outCtx);
    av_close_input_file(inCtx);
    return 0;
}