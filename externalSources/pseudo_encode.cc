// find output format
AVOutputFormat * outputFormat = av_guess_format("mp4", NULL, NULL);
AVFormatContext *outFmtCtx = NULL;

// create output cotext
avformat_alloc_output_context2(&outFmtCtx, outputFormat, NULL, NULL);

// create new stream
AVStream * outStrm = avformat_new_stream(outFmtCtx, codecEncode);
avcodec_get_context_defaults3(outStrm->codec, *codec);

outStrm->codec->codec_id = codec_id;
outStrm->codec->coder_type = AVMEDIA_TYPE_VIDEO;
/// outStrm->codec-> ... 
/// set all fields marked as "MUST be set by user" in avcodec.h
/// ....

// create file
avio_open2(&outFmtCtx->pb, file_name, AVIO_FLAG_WRITE, NULL, NULL);
avformat_write_header(outFmtCtx, NULL);

/// write packets
/// for ( )
av_interleaved_write_frame(outFmtCtx, packet);

/// finish
av_write_trailer(outFmtCtx);
avio_close(outFmtCtx->pb);
avformat_free_context(outFmtCtx);