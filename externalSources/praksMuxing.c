int VideoClipper::Init(const wxString& filename)
{
    int ret = 0;
    char errbuf[64];

    av_register_all();
    if ((ret = avformat_open_input( &m_informat, filename.mb_str(), 0, 0)) != 0 )
    {
        av_strerror(ret,errbuf,sizeof(errbuf));
        PRINT_VAL("Not able to Open file;; ", errbuf)
        ret = -1;
        return ret;
    }
    else
    {
        PRINT_MSG("Opened File ")
    }

    if ((ret = avformat_find_stream_info(m_informat, 0))< 0 )
    {

        av_strerror(ret,errbuf,sizeof(errbuf));
        PRINT_VAL("Not Able to find stream info:: ", errbuf)
        ret = -1;
        return ret;
    }
    else
    {
        PRINT_MSG("Got stream Info ")
    }

    for(unsigned int i = 0; i<m_informat->nb_streams; i++)
    {
        if(m_informat->streams[i]->codec->codec_type == AVMEDIA_TYPE_VIDEO)
        {

            PRINT_MSG("Found Video Stream ")
            m_in_vid_strm_idx = i;
            m_in_vid_strm = m_informat->streams[i];
        }

        if(m_informat->streams[i]->codec->codec_type == AVMEDIA_TYPE_AUDIO)
        {
            PRINT_MSG("Found Audio Stream ")
            m_in_aud_strm_idx = i;
            m_in_aud_strm = m_informat->streams[i];
        }
    }

    if(m_in_aud_strm_idx == -1 && m_in_vid_strm_idx == -1)
    {
       ret = -1;
    }

    if(m_informat->duration == AV_NOPTS_VALUE)
    {
        if(m_in_vid_strm_idx != -1 && m_informat->streams[m_in_vid_strm_idx])
        {
            if(m_informat->streams[m_in_vid_strm_idx]->duration != AV_NOPTS_VALUE)
            {
                //m_in_end_time = (m_informat->streams[m_in_vid_strm_idx]->duration)/(AV_TIME_BASE);
                m_in_end_time = (m_informat->streams[m_in_vid_strm_idx]->duration)/(m_informat->streams[m_in_vid_strm_idx]->time_base.den/m_informat->streams[m_in_vid_strm_idx]->time_base.num);

            }

        }
        else if(m_in_aud_strm_idx != -1 && m_informat->streams[m_in_aud_strm_idx])
        {
            if(m_informat->streams[m_in_aud_strm_idx]->duration != AV_NOPTS_VALUE)
            {
                m_in_end_time = (m_informat->streams[m_in_aud_strm_idx]->duration)/(AV_TIME_BASE);
            }
        }
    }
    else
    {
        m_in_end_time = (m_informat->duration)/(AV_TIME_BASE);
    }

    if(m_in_vid_strm_idx != -1 && m_informat->streams[m_in_vid_strm_idx])
    {
        if(m_informat->streams[m_in_vid_strm_idx]->r_frame_rate.num != AV_NOPTS_VALUE && m_informat->streams[m_in_vid_strm_idx]->r_frame_rate.den != 0)
        {
            m_fps =  (m_informat->streams[m_in_vid_strm_idx]->r_frame_rate.num)/ (m_informat->streams[m_in_vid_strm_idx]->r_frame_rate.den);
        }
    }
    else
    {
        m_fps = 25;
    }
    AVOutputFormat *outfmt = NULL;
    std::string outfile = std::string(filename) + "clip_out.avi";
    outfmt = av_guess_format(NULL,outfile.c_str(),NULL);

    if(outfmt == NULL)
    {
        ret = -1;
        return ret;
    }
    else
    {
        m_outformat = avformat_alloc_context();
        if(m_outformat)
        {
            m_outformat->oformat = outfmt;
            _snprintf(m_outformat->filename, sizeof(m_outformat->filename), "%s", outfile.c_str());
        }
        else
        {
            ret = -1;
            return ret;
        }
    }

    AVCodec *out_vid_codec,*out_aud_codec;
    out_vid_codec = out_aud_codec = NULL;

    if(outfmt->video_codec != AV_CODEC_ID_NONE && m_in_vid_strm != NULL)
    {
        out_vid_codec = avcodec_find_encoder(outfmt->video_codec);
        if(NULL == out_vid_codec)
        {
            PRINT_MSG("Could Not Find Vid Encoder")
            ret = -1;
            return ret;
        }
        else
        {
            PRINT_MSG("Found Out Vid Encoder ")
            m_out_vid_strm = avformat_new_stream(m_outformat, out_vid_codec);
            if(NULL == m_out_vid_strm)
            {
                 PRINT_MSG("Failed to Allocate Output Vid Strm ")
                 ret = -1;
                 return ret;
            }
            else
            {
                 PRINT_MSG("Allocated Video Stream ")
                 if(avcodec_copy_context(m_out_vid_strm->codec, m_informat->streams[m_in_vid_strm_idx]->codec) != 0)
                 {
                    PRINT_MSG("Failed to Copy Context ")
                    ret = -1;
                    return ret;
                 }
                 else
                 {
                    m_out_vid_strm->sample_aspect_ratio.den = m_out_vid_strm->codec->sample_aspect_ratio.den;
                    m_out_vid_strm->sample_aspect_ratio.num = m_in_vid_strm->codec->sample_aspect_ratio.num;
                    PRINT_MSG("Copied Context ")
                    m_out_vid_strm->codec->codec_id = m_in_vid_strm->codec->codec_id;
                    m_out_vid_strm->codec->time_base.num = 1;
                    m_out_vid_strm->codec->time_base.den = m_fps*(m_in_vid_strm->codec->ticks_per_frame);
                    m_out_vid_strm->time_base.num = 1;
                    m_out_vid_strm->time_base.den = 1000;
                    m_out_vid_strm->r_frame_rate.num = m_fps;
                    m_out_vid_strm->r_frame_rate.den = 1;
                    m_out_vid_strm->avg_frame_rate.den = 1;
                    m_out_vid_strm->avg_frame_rate.num = m_fps;
                    m_out_vid_strm->duration = (m_out_end_time - m_out_start_time)*1000;
                 }
               }
            }
      }

    if(outfmt->audio_codec != AV_CODEC_ID_NONE && m_in_aud_strm != NULL)
    {
        out_aud_codec = avcodec_find_encoder(outfmt->audio_codec);
        if(NULL == out_aud_codec)
        {
            PRINT_MSG("Could Not Find Out Aud Encoder ")
            ret = -1;
            return ret;
        }
        else
        {
            PRINT_MSG("Found Out Aud Encoder ")
            m_out_aud_strm = avformat_new_stream(m_outformat, out_aud_codec);
            if(NULL == m_out_aud_strm)
            {
                PRINT_MSG("Failed to Allocate Out Vid Strm ")
                ret = -1;
                return ret;
            }
            else
            {
                if(avcodec_copy_context(m_out_aud_strm->codec, m_informat->streams[m_in_aud_strm_idx]->codec) != 0)
                {
                    PRINT_MSG("Failed to Copy Context ")
                    ret = -1;
                    return ret;
                }
                else
                 {
                    PRINT_MSG("Copied Context ")
                    m_out_aud_strm->codec->codec_id = m_in_aud_strm->codec->codec_id;
                    m_out_aud_strm->codec->codec_tag = 0;
                    m_out_aud_strm->pts = m_in_aud_strm->pts;
                    m_out_aud_strm->duration = m_in_aud_strm->duration;
                    m_out_aud_strm->time_base.num = m_in_aud_strm->time_base.num;
                    m_out_aud_strm->time_base.den = m_in_aud_strm->time_base.den;

                }
            }
         }
      }

      if (!(outfmt->flags & AVFMT_NOFILE))
      {
        if (avio_open2(&m_outformat->pb, outfile.c_str(), AVIO_FLAG_WRITE,NULL, NULL) < 0)
        {
                PRINT_VAL("Could Not Open File ", outfile)
                ret = -1;
                return ret;
        }
      }
        /* Write the stream header, if any. */
      if (avformat_write_header(m_outformat, NULL) < 0)
      {
            PRINT_VAL("Error Occurred While Writing Header ", outfile)
            ret = -1;
            return ret;
      }
      else
      {
            PRINT_MSG("Written Output header ")
            m_init_done = true;
      }

    return ret;
}

int VideoClipper::GenerateClip(void)
{
    AVPacket pkt, outpkt;
    int aud_pts = 0, vid_pts = 0, aud_dts = 0, vid_dts = 0;
    int last_vid_pts = 0;
    if(m_good_clip)
    {
        SeekFrame();
        while(av_read_frame(m_informat, &pkt) >= 0 && (m_num_frames-- > 0))
        {
            if(pkt.stream_index == m_in_vid_strm_idx)
            {
                PRINT_VAL("ACTUAL VID Pkt PTS ",av_rescale_q(pkt.pts,m_in_vid_strm->time_base, m_in_vid_strm->codec->time_base))
                PRINT_VAL("ACTUAL VID Pkt DTS ", av_rescale_q(pkt.dts, m_in_vid_strm->time_base, m_in_vid_strm->codec->time_base ))
                av_init_packet(&outpkt);
                if(pkt.pts != AV_NOPTS_VALUE)
                {
                    if(last_vid_pts == vid_pts)
                    {
                        vid_pts++;
                        last_vid_pts = vid_pts;
                    }
                    outpkt.pts = vid_pts;
                    PRINT_VAL("ReScaled VID Pts ", outpkt.pts)
                }
                else
                {
                    outpkt.pts = AV_NOPTS_VALUE;
                }

                if(pkt.dts == AV_NOPTS_VALUE)
                {
                    outpkt.dts = AV_NOPTS_VALUE;
                }
                else
                {
                    outpkt.dts = vid_pts;
                    PRINT_VAL("ReScaled VID Dts ", outpkt.dts)
                    PRINT_MSG("=======================================")
                }

                outpkt.data = pkt.data;
                outpkt.size = pkt.size;
                outpkt.stream_index = pkt.stream_index;
                outpkt.flags |= AV_PKT_FLAG_KEY;
                last_vid_pts = vid_pts;
                if(av_interleaved_write_frame(m_outformat, &outpkt) < 0)
                {
                    PRINT_MSG("Failed Video Write ")
                }
                else
                {
                    m_out_vid_strm->codec->frame_number++;
                }
                av_free_packet(&outpkt);
                av_free_packet(&pkt);
            }
            else if(pkt.stream_index == m_in_aud_strm_idx)
            {
                PRINT_VAL("ACTUAL AUD Pkt PTS ", av_rescale_q(pkt.pts, m_in_aud_strm->time_base, m_in_aud_strm->codec->time_base))
                PRINT_VAL("ACTUAL AUD Pkt DTS ", av_rescale_q(pkt.dts, m_in_aud_strm->time_base, m_in_aud_strm->codec->time_base))
                //num_aud_pkt++;
                av_init_packet(&outpkt);
                if(pkt.pts != AV_NOPTS_VALUE)
                {
                    outpkt.pts = aud_pts;
                    PRINT_VAL("ReScaled AUD PTS ", outpkt.pts)
                }
                else
                {
                    outpkt.pts = AV_NOPTS_VALUE;
                }

                if(pkt.dts == AV_NOPTS_VALUE)
                {
                    outpkt.dts = AV_NOPTS_VALUE;
                }
                else
                {
                    outpkt.dts = aud_pts;
                    PRINT_VAL("ReScaled AUD DTS ", outpkt.dts)
                    PRINT_MSG("====================================")
                    if( outpkt.pts >= outpkt.dts)
                    {
                        outpkt.dts = outpkt.pts;
                    }
                    if(outpkt.dts == aud_dts)
                    {
                        outpkt.dts++;
                    }
                    if(outpkt.pts < outpkt.dts)
                    {
                        outpkt.pts = outpkt.dts;
                        aud_pts = outpkt.pts;
                    }
                }

                outpkt.data = pkt.data;
                outpkt.size = pkt.size;
                outpkt.stream_index = pkt.stream_index;
                outpkt.flags |= AV_PKT_FLAG_KEY;
                vid_pts = aud_pts;
                aud_pts++;
                if(av_interleaved_write_frame(m_outformat, &outpkt) < 0)
                {
                    PRINT_MSG("Faile Audio Write ")
                }
                else
                {
                    m_out_aud_strm->codec->frame_number++;
                }
                av_free_packet(&outpkt);
                av_free_packet(&pkt);
        }
        else
        {
            PRINT_MSG("Got Unknown Pkt ")
            //num_unkwn_pkt++;
        }
        //num_total_pkt++;
    }

    av_write_trailer(m_outformat);
    av_free_packet(&outpkt);
    av_free_packet(&pkt);
    return 0;
 }
    return -1;
}