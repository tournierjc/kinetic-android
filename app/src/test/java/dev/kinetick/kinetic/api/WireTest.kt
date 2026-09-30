package dev.kinetick.kinetic.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Decoder tests against payloads captured from a live `kcode --server`
 * (kinetick-code 0.6.6). Payloads are base64-encoded so no escaping can
 * silently alter them.
 */
class WireTest {

    private val HEALTH = "eyJvayI6dHJ1ZSwidmVyc2lvbiI6IjAuNi42In0="
    private val SESSIONS = "eyJzZXNzaW9ucyI6W3sic2Vzc2lvbklkIjoibXZzX2M3YjU1NjQwM2ZhMTRkMGViM2Q1YWE0Y2Y2YTMzMTlkIiwiYWdlbnROYW1lIjoibWF2aXMiLCJ0aXRsZSI6IlYyIHByb2JlIiwic2Vzc2lvblR5cGUiOiJicmFuY2giLCJzZXNzaW9uS2luZCI6ImNvbnZlcnNhdGlvbiIsInZpc2liaWxpdHkiOiJ2aXNpYmxlIiwiYXJjaGl2ZWQiOmZhbHNlLCJ3b3Jrc3BhY2VEaXIiOiIvdG1wL2tpbmV0aWMtdjIiLCJjcmVhdGVkQXQiOjE3OTA0MDgyNjIzNzgsInVwZGF0ZWRBdCI6MTc5MDQwODI3ODU5NSwic3RhdHVzIjoiZXJyb3IiLCJlcnJvck1lc3NhZ2UiOiJMb2NhbE1vZGVsUmVzb2x2ZXI6IG1hbmFnZWQgT0F1dGggYmVhcmVyIGlzIG5vdCBzeW5jZWQgZm9yIHByb3ZpZGVyIFwibWluaW1heFwiLiIsIm1vZGVsIjp7InByb3ZpZGVySWQiOiJtaW5pbWF4IiwibW9kZWxJZCI6Ik1pbmlNYXgtTTMiLCJ2YXJpYW50IjoidGhpbmtpbmcifX0seyJzZXNzaW9uSWQiOiJtdnNfYmYwZWYxZjA3OWNkNDFjM2IzMGZjMTM4ZGRhNDM0NTYiLCJhZ2VudE5hbWUiOiJtYXZpcyIsInRpdGxlIjoiS2luZXRpYyBFMkUiLCJzZXNzaW9uVHlwZSI6ImJyYW5jaCIsInNlc3Npb25LaW5kIjoiY29udmVyc2F0aW9uIiwidmlzaWJpbGl0eSI6InZpc2libGUiLCJhcmNoaXZlZCI6ZmFsc2UsIndvcmtzcGFjZURpciI6Ii90bXAva2luZXRpYy1lMmUiLCJjcmVhdGVkQXQiOjE3OTA0MDE2NDA4NDIsInVwZGF0ZWRBdCI6MTc5MDQwMTY0MDg0Miwic3RhdHVzIjoiaWRsZSIsIm1vZGVsIjp7InByb3ZpZGVySWQiOiJtaW5pbWF4IiwibW9kZWxJZCI6Ik1pbmlNYXgtTTMiLCJ2YXJpYW50IjoidGhpbmtpbmcifX1dLCJoYXNNb3JlIjpmYWxzZX0="
    private val SESSION = "eyJzZXNzaW9uSWQiOiJtdnNfYzdiNTU2NDAzZmExNGQwZWIzZDVhYTRjZjZhMzMxOWQiLCJhZ2VudE5hbWUiOiJtYXZpcyIsInRpdGxlIjoiVjIgcHJvYmUiLCJzZXNzaW9uVHlwZSI6ImJyYW5jaCIsInNlc3Npb25LaW5kIjoiY29udmVyc2F0aW9uIiwidmlzaWJpbGl0eSI6InZpc2libGUiLCJhcmNoaXZlZCI6ZmFsc2UsIndvcmtzcGFjZURpciI6Ii90bXAva2luZXRpYy12MiIsImNyZWF0ZWRBdCI6MTc5MDQwODI2MjM3OCwidXBkYXRlZEF0IjoxNzkwNDA4Mjc4NTk1LCJzdGF0dXMiOiJlcnJvciIsImVycm9yTWVzc2FnZSI6IkxvY2FsTW9kZWxSZXNvbHZlcjogbWFuYWdlZCBPQXV0aCBiZWFyZXIgaXMgbm90IHN5bmNlZCBmb3IgcHJvdmlkZXIgXCJtaW5pbWF4XCIuIiwibW9kZWwiOnsicHJvdmlkZXJJZCI6Im1pbmltYXgiLCJtb2RlbElkIjoiTWluaU1heC1NMyIsInZhcmlhbnQiOiJ0aGlua2luZyJ9fQ=="
    private val MESSAGES = "eyJtZXNzYWdlcyI6W3siaWQiOiJtc2ctdXNlci12MS1wb2FLcXVYRjZubEIwVGpVak9lN2JUMnpsdHNHc1NtcjFXS255aVFNbmtNIiwidHVybklkIjoidHVybl84YmMyYTFhNS0xNWVjLTRhNDQtOGFiOS0zOTcyOThlMjUxNjEiLCJyb2xlIjoidXNlciIsInNvdXJjZSI6ImFwaSIsImNvbnRlbnQiOiJSZXBseSB3aXRoIGV4YWN0bHk6IFBPTkciLCJ0aW1lc3RhbXAiOjE3OTA0MDgyNzg0OTIsImFjdGlvbnMiOnsiZm9yayI6ZmFsc2UsInJld2luZCI6dHJ1ZX19XSwiaGFzTW9yZSI6ZmFsc2V9"
    private val INTERACTIONS = "eyJzZXNzaW9uSWQiOiJtdnNfYzdiNTU2NDAzZmExNGQwZWIzZDVhYTRjZjZhMzMxOWQiLCJwZXJtaXNzaW9ucyI6W10sImFjdGl2ZVJ1biI6eyJzY2hlbWFWZXJzaW9uIjoxLCJzZXNzaW9uSWQiOiJtdnNfYzdiNTU2NDAzZmExNGQwZWIzZDVhYTRjZjZhMzMxOWQiLCJzdGF0ZSI6InRlcm1pbmFsIiwiYWN0aW9ucyI6eyJzdGVlciI6ZmFsc2V9fX0="
    private val QUEUE = "eyJpdGVtcyI6W10sInBhdXNlZCI6ZmFsc2UsInBlbmRpbmdDb3VudCI6MH0="
    private val DELEGATION = "eyJzY2hlbWFWZXJzaW9uIjoxLCJyb290U2Vzc2lvbklkIjoibXZzX2M3YjU1NjQwM2ZhMTRkMGViM2Q1YWE0Y2Y2YTMzMTlkIiwibWVtYmVycyI6W119"
    private val USAGE = "eyJzdW1tYXJ5Ijp7ImlucHV0VG9rZW5zIjowLCJvdXRwdXRUb2tlbnMiOjAsInJlYXNvbmluZ1Rva2VucyI6MCwiY2FjaGVSZWFkVG9rZW5zIjowLCJjYWNoZVdyaXRlVG9rZW5zIjowLCJjb3N0VXNkIjowLCJ0dXJucyI6MCwidG90YWxUb2tlbnMiOjB9LCJyb3dzIjpbXX0="
    private val CONTEXT = "eyJzdGF0dXMiOiJlbXB0eSIsIm1vZGVsIjp7InByb3ZpZGVyIjoibWluaW1heCIsImlkIjoiTWluaU1heC1NMyIsImNvbnRleHRXaW5kb3ciOjUxMjAwMH19"
    private val FORK = "eyJjYW5Gb3JrIjpmYWxzZSwidW5hdmFpbGFibGVSZWFzb24iOiJtZXNzYWdlLWJvdW5kYXJ5LW5vdC1mb3VuZCIsInN1Z2dlc3RlZFRpdGxlIjoiMSAtIFYyIHByb2JlIiwibmV4dEZvcmtPcmRpbmFsIjoxLCJzb3VyY2VUaXRsZSI6IlYyIHByb2JlIiwid29ya3RyZWVWaXNpYmxlIjpmYWxzZSwid29ya3RyZWVFbGlnaWJsZSI6ZmFsc2V9"
    private val BTASKS = "W10="
    private val SKILLS = "eyJza2lsbHMiOiBbeyJuYW1lIjogImNvZGUtcmV2aWV3IiwgImRlc2NyaXB0aW9uIjogIlJldmlldyBsb2NhbCB1bmNvbW1pdHRlZCBjaGFuZ2VzLCBjb21taXRzLCBicmFuY2hlcywgcHVsbCByZXF1ZXN0cywgZmlsZXMsIGZ1bmN0aW9ucywgb3Igb3RoZXIgdXNlci1zcGVjaWZpZWQgY29kZSBzY29wZXMgZm9yIGNvbmNyZXRlIGRlZmVjdHMuIEZvbGxvdyB0aGUgc2NvcGUgYW5kIGNvbXBhcmlzb24gYmFzZSBuYW1lZCBieSB0aGUgdXNlci4gRG8gbm90IHVzZSBmb3Igb3JkaW5hcnkgY29kZSBleHBsYW5hdGlvbiwgZGVidWdnaW5nLCBpbXBsZW1lbnRhdGlvbiwgb3IgZml4IHJlcXVlc3RzIHRoYXQgZG8gbm90IGFzayBmb3IgYSByZXZpZXcuIiwgImRpc3BsYXlEZXNjcmlwdGlvbiI6ICJSZXZpZXcgbG9jYWwgdW5jb21taXR0ZWQgY2hhbmdlcywgY29tbWl0cywgYnJhbmNoZXMsIHB1bGwgcmVxdWVzdHMsIGZpbGVzLCBmdW5jdGlvbnMsIG9yIG90aGVyIHVzZXItc3BlY2lmaWVkIGNvZGUgc2NvcGVzIGZvciBjb25jcmV0ZSBkZWZlY3RzLiBGb2xsb3cgdGhlIHNjb3BlIGFuZCBjb21wYXJpc29uIGJhc2UgbmFtZWQgYnkgdGhlIHVzZXIuIERvIG5vdCB1c2UgZm9yIG9yZGluYXJ5IGNvZGUgZXhwbGFuYXRpb24sIGRlYnVnZ2luZywgaW1wbGVtZW50YXRpb24sIG9yIGZpeCByZXF1ZXN0cyB0aGF0IGRvIG5vdCBhc2sgZm9yIGEgcmV2aWV3LiIsICJzY29wZSI6IDIsICJzb3VyY2VUeXBlIjogMSwgInNvdXJjZUtpbmQiOiAiYnVpbHRpbi1nbG9iYWwiLCAidXBkYXRlZEF0IjogMTc5MDQwMTYxNjk4MywgImxvY2F0aW9uVXJpIjogImZpbGVzOi8vL29wdC9kYXRhL3Byb2ZpbGVzL2JhbmN0b2wvaG9tZS8ua2luZXRpY2svLmJ1aWx0aW4tc2tpbGxzL2NvZGUtcmV2aWV3L1NLSUxMLm1kIiwgImVuYWJsZWQiOiB0cnVlfSwgeyJuYW1lIjogImRlZXAtcmVzZWFyY2giLCAiZGVzY3JpcHRpb24iOiAiVXNlIHRoaXMgc2tpbGwgZm9yIGNvbXBsZXgsIG9wZW4tZW5kZWQgRGVlcCBSZXNlYXJjaCB0YXNrcyB0aGF0IHJlcXVpcmUgZXh0ZXJuYWwgaW5mb3JtYXRpb24gdmVyaWZpY2F0aW9uLiBJdCBpcyBzdWl0YWJsZSBmb3IgbWFya2V0L2luZHVzdHJ5IGFuYWx5c2lzLCB0ZWNobmljYWwgcmVzZWFyY2gsIGNvbXBldGl0b3IgcmVzZWFyY2gsIHRyZW5kIGp1ZGdtZW50LCBwb2xpY3kvYWNhZGVtaWMvZmFjdCB2ZXJpZmljYXRpb24sIGFuZCBsb25nIGFuc3dlcnMgdGhhdCBuZWVkIHNvdXJjZSBjaXRhdGlvbnMuIFRoaXMgc2tpbGwgY29tcGxldGVzIHRoZSByZXNlYXJjaCB0aHJvdWdoIGZpdmUgY29uc2VjdXRpdmUgc3RlcCBwcm9tcHRzOiBTdGVwIDEgY29uZmlybXMgZmFjdHVhbCBiYWNrZ3JvdW5kIG9ubHk7IFN0ZXAgMiB1bmRlcnN0YW5kcyB0aGUgcXVlc3Rpb24gYW5kIGp1ZGdlcyB0aGUgZGlyZWN0aW9uOyBTdGVwIDMgcGVyZm9ybXMgZGVlcCBhbmFseXNpcyBhbmQgcmVzZWFyY2ggcGxhbm5pbmc7IFN0ZXAgNCBzZWFyY2hlcywgdmVyaWZpZXMsIGFuZCBmb3JtcyByZXNlYXJjaCB1bmRlcnN0YW5kaW5nIGFjY29yZGluZyB0byB0aGUgcGxhbjsgU3RlcCA1IHdyaXRlcyB0aGUgY3VycmVudC10dXJuIGZpbmFsIGFuc3dlciBmaWxlIGJhc2VkIG9uIHRoZSBmaXJzdCBmb3VyIHN0ZXBzLiBFeGVjdXRpb24gbXVzdCBmb2xsb3cgc3RlcCBvcmRlcjogZWFjaCBzdGVwIHByb21wdCBmaWxlIG11c3QgYmUgcmVhZCBieSBhbiBleHBsaWNpdCBSZWFkIHRvb2wgY2FsbCBiZWZvcmUgdGhhdCBzdGVwIHN0YXJ0cy4gRG8gbm90IHNraXAgc3RlcHMsIHJlb3JkZXIgc3RlcHMsIHJlYWQgbGF0ZXIgc3RlcHMgZWFybHksIG9yIHRyZWF0IHRoZSBzdGVwcyBhcyBpbmRlcGVuZGVudCB0YXNrcy4gQSB0cmFjZSB0aGF0IG1pc3NlcyBhbnkgc3RlcCBwcm9tcHQgaXMgaW52YWxpZC4iLCAiZGlzcGxheURlc2NyaXB0aW9uIjogIlVzZSB0aGlzIHNraWxsIGZvciBjb21wbGV4LCBvcGVuLWVuZGVkIERlZXAgUmVzZWFyY2ggdGFza3MgdGhhdCByZXF1aXJlIGV4dGVybmFsIGluZm9ybWF0aW9uIHZlcmlmaWNhdGlvbi4gSXQgaXMgc3VpdGFibGUgZm9yIG1hcmtldC9pbmR1c3RyeSBhbmFseXNpcywgdGVjaG5pY2FsIHJlc2VhcmNoLCBjb21wZXRpdG9yIHJlc2VhcmNoLCB0cmVuZCBqdWRnbWVudCwgcG9saWN5L2FjYWRlbWljL2ZhY3QgdmVyaWZpY2F0aW9uLCBhbmQgbG9uZyBhbnN3ZXJzIHRoYXQgbmVlZCBzb3VyY2UgY2l0YXRpb25zLiBUaGlzIHNraWxsIGNvbXBsZXRlcyB0aGUgcmVzZWFyY2ggdGhyb3VnaCBmaXZlIGNvbnNlY3V0aXZlIHN0ZXAgcHJvbXB0czogU3RlcCAxIGNvbmZpcm1zIGZhY3R1YWwgYmFja2dyb3VuZCBvbmx5OyBTdGVwIDIgdW5kZXJzdGFuZHMgdGhlIHF1ZXN0aW9uIGFuZCBqdWRnZXMgdGhlIGRpcmVjdGlvbjsgU3RlcCAzIHBlcmZvcm1zIGRlZXAgYW5hbHlzaXMgYW5kIHJlc2VhcmNoIHBsYW5uaW5nOyBTdGVwIDQgc2VhcmNoZXMsIHZlcmlmaWVzLCBhbmQgZm9ybXMgcmVzZWFyY2ggdW5kZXJzdGFuZGluZyBhY2NvcmRpbmcgdG8gdGhlIHBsYW47IFN0ZXAgNSB3cml0ZXMgdGhlIGN1cnJlbnQtdHVybiBmaW5hbCBhbnN3ZXIgZmlsZSBiYXNlZCBvbiB0aGUgZmlyc3QgZm91ciBzdGVwcy4gRXhlY3V0aW9uIG11c3QgZm9sbG93IHN0ZXAgb3JkZXI6IGVhY2ggc3RlcCBwcm9tcHQgZmlsZSBtdXN0IGJlIHJlYWQgYnkgYW4gZXhwbGljaXQgUmVhZCB0b29sIGNhbGwgYmVmb3JlIHRoYXQgc3RlcCBzdGFydHMuIERvIG5vdCBza2lwIHN0ZXBzLCByZW9yZGVyIHN0ZXBzLCByZWFkIGxhdGVyIHN0ZXBzIGVhcmx5LCBvciB0cmVhdCB0aGUgc3RlcHMgYXMgaW5kZXBlbmRlbnQgdGFza3MuIEEgdHJhY2UgdGhhdCBtaXNzZXMgYW55IHN0ZXAgcHJvbXB0IGlzIGludmFsaWQuIiwgInNjb3BlIjogMiwgInNvdXJjZVR5cGUiOiAxLCAic291cmNlS2luZCI6ICJidWlsdGluLWdsb2JhbCIsICJkaXNwbGF5TmFtZXMiOiB7InpoLUhhbnMiOiAiXHU2ZGYxXHU1ZWE2XHU3ODE0XHU3YTc2In0sICJkZXNjcmlwdGlvbnMiOiB7InpoLUhhbnMiOiAiXHU3NTI4XHU0ZThlXHU1OTBkXHU2NzQyXHUzMDAxXHU1ZjAwXHU2NTNlXHU1ZjBmXHU2ZGYxXHU1ZWE2XHU3ODE0XHU3YTc2XHU0ZWZiXHU1MmExXHVmZjBjXHU5NzAwXHU4OTgxXHU1OTE2XHU5MGU4XHU0ZmUxXHU2MDZmXHU2ODM4XHU5YThjXHUzMDAxXHU4ZGU4XHU2NzY1XHU2ZTkwXHU2NDFjXHU3ZDIyXHUzMDAxXHU0ZThiXHU1YjllXHU2ODIxXHU5YThjXHU1NDhjXHU1ZTI2XHU1ZjE1XHU3NTI4XHU3Njg0XHU5NTdmXHU3YjU0XHU2ODQ4XHUzMDAyIFx1OGJlNVx1NjI4MFx1ODBmZFx1NjMwOVx1ODBjY1x1NjY2Zlx1Nzg2ZVx1OGJhNFx1MzAwMVx1NjViOVx1NTQxMVx1NTIyNFx1NjVhZFx1MzAwMVx1NmRmMVx1NWVhNlx1NTIwNlx1Njc5MFx1MzAwMVx1NjQxY1x1N2QyMlx1NjgzOFx1OWE4Y1x1MzAwMVx1NjcwMFx1N2VjOFx1NTE5OVx1NGY1Y1x1NGU5NFx1NGUyYVx1NmI2NVx1OWFhNFx1OTg3YVx1NWU4Zlx1NjI2N1x1ODg0Y1x1ZmYwY1x1NmJjZlx1NGUwMFx1NmI2NVx1OTBmZFx1NWZjNVx1OTg3Ylx1NTE0OFx1NjYzZVx1NWYwZlx1OGJmYlx1NTNkNlx1NWJmOVx1NWU5NCBzdGVwIHByb21wdFx1MzAwMiJ9LCAidXBkYXRlZEF0IjogMTc5MDQwMTYxNjk4MywgImxvY2F0aW9uVXJpIjogImZpbGVzOi8vL29wdC9kYXRhL3Byb2ZpbGVzL2JhbmN0b2wvaG9tZS8ua2luZXRpY2svLmJ1aWx0aW4tc2tpbGxzL2RlZXAtcmVzZWFyY2gvU0tJTEwubWQiLCAiZW5hYmxlZCI6IHRydWV9XSwgImhhc01vcmUiOiBmYWxzZX0="
    private val MODELS = "W3sicHJvdmlkZXJJZCI6ICJtaW5pbWF4IiwgIm1vZGVsSWQiOiAiTWluaU1heC1NMyIsICJtb2RlbENvbmZpZ0lkIjogIm1pbmltYXgvTWluaU1heC1NMyIsICJkaXNwbGF5TmFtZSI6ICJNaW5pTWF4LU0zIiwgImVuYWJsZWQiOiB0cnVlLCAic2VsZWN0ZWQiOiB0cnVlLCAicHJvdmlkZXJTb3VyY2UiOiAicHJvdmlkZXIiLCAicHJvdmlkZXJLaW5kIjogIm1pbmltYXgtbWFuYWdlZCIsICJwcm92aWRlck5hbWUiOiAiTWluaU1heCIsICJjb250ZXh0TGltaXQiOiA1MTIwMDAsICJkZWZhdWx0Q29udGV4dExpbWl0IjogNTEyMDAwLCAiY29udGV4dFdpbmRvd09wdGlvbnMiOiBbNTEyMDAwLCAxMDAwMDAwXSwgIm1heE91dHB1dFRva2VucyI6IDEyODAwMCwgImNvbnRleHRXaW5kb3dPcHRpb25IaW50cyI6IHsiMTAwMDAwMCI6ICJoaWdoZXJfdXNhZ2UifSwgIm1vZGFsaXRpZXMiOiB7ImlucHV0IjogWyJ0ZXh0IiwgImltYWdlIiwgInZpZGVvIl0sICJvdXRwdXQiOiBbInRleHQiXX0sICJzdXBwb3J0ZWRWYXJpYW50cyI6IFsiIiwgInRoaW5raW5nIl0sICJ0aGlua2luZ0NvbmZpZyI6IHsibW9kZSI6ICJzd2l0Y2hhYmxlIiwgImRlZmF1bHRWYWx1ZSI6ICJ0cnVlIn0sICJ2YXJpYW50IjogInRoaW5raW5nIn0sIHsicHJvdmlkZXJJZCI6ICJtaW5pbWF4IiwgIm1vZGVsSWQiOiAiTWluaU1heC1NMi43LWhpZ2hzcGVlZCIsICJtb2RlbENvbmZpZ0lkIjogIm1pbmltYXgvTWluaU1heC1NMi43LWhpZ2hzcGVlZCIsICJkaXNwbGF5TmFtZSI6ICJNaW5pTWF4LU0yLjctaGlnaHNwZWVkIiwgImVuYWJsZWQiOiB0cnVlLCAic2VsZWN0ZWQiOiBmYWxzZSwgInByb3ZpZGVyU291cmNlIjogInByb3ZpZGVyIiwgInByb3ZpZGVyS2luZCI6ICJtaW5pbWF4LW1hbmFnZWQiLCAicHJvdmlkZXJOYW1lIjogIk1pbmlNYXgiLCAiY29udGV4dExpbWl0IjogMjAwMDAwLCAiZGVmYXVsdENvbnRleHRMaW1pdCI6IDIwMDAwMCwgIm1heE91dHB1dFRva2VucyI6IDEyODAwMCwgIm1vZGFsaXRpZXMiOiB7ImlucHV0IjogWyJ0ZXh0Il0sICJvdXRwdXQiOiBbInRleHQiXX19XQ=="
    private val PERMISSIONS = "W10="

    private val FRAME_0 = "eyJ0eXBlIjoiaGVhcnRiZWF0In0="
    private val FRAME_1 = "eyJ0eXBlIjoibWVzc2FnZSIsIm1lc3NhZ2UiOnsiaWQiOiJtc2ctdXNlci12MS1wb2FLcXVYRjZubEIwVGpVak9lN2JUMnpsdHNHc1NtcjFXS255aVFNbmtNIiwidHVybklkIjoidHVybl84YmMyYTFhNS0xNWVjLTRhNDQtOGFiOS0zOTcyOThlMjUxNjEiLCJyb2xlIjoidXNlciIsInNvdXJjZSI6ImFwaSIsImNvbnRlbnQiOiJSZXBseSB3aXRoIGV4YWN0bHk6IFBPTkciLCJ0aW1lc3RhbXAiOjE3OTA0MDgyNzg0OTJ9LCJjdXJzb3IiOiJzc2UxOnNlc3Npb24lM0FtdnNfYzdiNTU2NDAzZmExNGQwZWIzZDVhYTRjZjZhMzMxOWQ6YTgxYmFkOTUtMjRjZC00OWJlLWIzNDItZGQ5Njc2NGUwMDllOjEifQ=="
    private val FRAME_2 = "eyJ0eXBlIjoiZ2VuZXJpYyIsImV2ZW50VHlwZSI6InF1ZXJ5X2NvbGxhcHNlX3ZpZXciLCJkYXRhIjp7InF1ZXJ5X2NvbGxhcHNlX3ZpZXciOnsicXVlcnlfa2V5IjoidHVybjp0dXJuXzhiYzJhMWE1LTE1ZWMtNGE0NC04YWI5LTM5NzI5OGUyNTE2MSIsImN1cnJlbnRfdHVybl9pZCI6InR1cm5fOGJjMmExYTUtMTVlYy00YTQ0LThhYjktMzk3Mjk4ZTI1MTYxIiwiZm9yY2VfZXhwYW5kZWQiOmZhbHNlLCJwcm9jZXNzaW5nX3N0YXJ0ZWRfYXRfbXMiOjE3OTA0MDgyNzg0OTN9fSwiY3Vyc29yIjoic3NlMTpzZXNzaW9uJTNBbXZzX2M3YjU1NjQwM2ZhMTRkMGViM2Q1YWE0Y2Y2YTMzMTlkOmE4MWJhZDk1LTI0Y2QtNDliZS1iMzQyLWRkOTY3NjRlMDA5ZToyIn0="
    private val FRAME_3 = "eyJ0eXBlIjoiZ2VuZXJpYyIsImV2ZW50VHlwZSI6InF1ZXJ5X2NvbGxhcHNlX3ZpZXciLCJkYXRhIjp7InF1ZXJ5X2NvbGxhcHNlX3ZpZXciOnsicXVlcnlfa2V5IjoidHVybjp0dXJuXzhiYzJhMWE1LTE1ZWMtNGE0NC04YWI5LTM5NzI5OGUyNTE2MSIsImN1cnJlbnRfdHVybl9pZCI6InR1cm5fOGJjMmExYTUtMTVlYy00YTQ0LThhYjktMzk3Mjk4ZTI1MTYxIiwiZm9yY2VfZXhwYW5kZWQiOnRydWUsInByb2Nlc3Npbmdfc3RhcnRlZF9hdF9tcyI6MTc5MDQwODI3ODQ5MywicHJvY2Vzc2luZ19maW5pc2hlZF9hdF9tcyI6MTc5MDQwODI3ODU5Nn19LCJjdXJzb3IiOiJzc2UxOnNlc3Npb24lM0FtdnNfYzdiNTU2NDAzZmExNGQwZWIzZDVhYTRjZjZhMzMxOWQ6YTgxYmFkOTUtMjRjZC00OWJlLWIzNDItZGQ5Njc2NGUwMDllOjMifQ=="
    private val FRAME_4 = "eyJ0eXBlIjoic2Vzc2lvbi1zdGF0dXMiLCJzdGF0dXMiOiJlcnJvciIsIm1lc3NhZ2UiOiJMb2NhbE1vZGVsUmVzb2x2ZXI6IG1hbmFnZWQgT0F1dGggYmVhcmVyIGlzIG5vdCBzeW5jZWQgZm9yIHByb3ZpZGVyIFwibWluaW1heFwiLiIsImN1cnNvciI6InNzZTE6c2Vzc2lvbiUzQW12c19jN2I1NTY0MDNmYTE0ZDBlYjNkNWFhNGNmNmEzMzE5ZDphODFiYWQ5NS0yNGNkLTQ5YmUtYjM0Mi1kZDk2NzY0ZTAwOWU6NCJ9"
    private val FRAME_5 = "eyJ0eXBlIjoiZG9uZSJ9"
    private val FRAME_6 = "e30="

    private val FRAMES_TYPES = arrayOf("heartbeat", "message", "generic", "generic", "session-status", "done", "end")
    private val FRAMES = arrayOf(FRAME_0, FRAME_1, FRAME_2, FRAME_3, FRAME_4, FRAME_5, FRAME_6)

    private fun dec(s: String): String = String(Base64.getDecoder().decode(s))

    @Test
    fun health() {
        val o = Wire.obj(dec(HEALTH))!!
        assertEquals(true, o.get("ok").asBoolean)
        assertNotNull(o.get("version").asString)
    }

    @Test
    fun sessionPage() {
        val page = Wire.sessionPage(dec(SESSIONS))
        assertTrue(page.sessions.size >= 1)
        val s = page.sessions.first()
        assertTrue(s.sessionId.isNotBlank())
        assertNotNull(s.workspaceDir)
    }

    @Test
    fun childSessionFields() {
        val s = Wire.session(
            """{"sessionId":"c1","sessionKind":"task","parentSessionId":"p1","visibility":"visible","title":"scan"}"""
        )!!
        assertEquals("task", s.sessionKind)
        assertEquals("p1", s.parentSessionId)
        assertEquals("visible", s.visibility)
        assertTrue(s.isSubagentSession())
    }

    @Test
    fun singleSession() {
        val s = Wire.session(dec(SESSION))!!
        assertEquals("V2 probe", s.title)
        assertEquals("mavis", s.agentName)
        assertEquals("/tmp/kinetic-v2", s.workspaceDir)
        // The captured session had a failed turn (no model credentials synced).
        assertEquals("error", s.status)
        assertNotNull(s.model?.modelId)
    }

    @Test
    fun messageHistory() {
        val page = Wire.messagePage(dec(MESSAGES))
        assertEquals(1, page.messages.size)
        assertEquals(false, page.hasMore)
        val m = page.messages.first()
        assertEquals("user", m.role)
        assertEquals("Reply with exactly: PONG", m.content)
        assertNotNull(m.id)
        assertNotNull(m.turnId)
    }

    @Test
    fun interactionsAfterFailedTurn() {
        val i = Wire.interactions(dec(INTERACTIONS))
        assertEquals("terminal", i.activeRun?.state)
        assertEquals(0, i.permissions.size)
        assertNull(i.questionnaire)
    }

    @Test
    fun queueEmpty() {
        val q = Wire.queue(dec(QUEUE))
        assertEquals(0, q.pendingCount)
        assertEquals(false, q.paused)
        assertEquals(0, q.items.size)
    }

    @Test
    fun delegationEmpty() {
        val d = Wire.delegation(dec(DELEGATION))
        assertNotNull(d.rootSessionId)
        assertEquals(0, d.members.size)
    }

    @Test
    fun usageZero() {
        val u = Wire.usage(dec(USAGE))
        assertEquals(0, u.summary.turns)
        assertEquals(0L, u.summary.totalTokens)
        assertEquals(0, u.rows.size)
    }

    @Test
    fun contextWindow() {
        val c = Wire.context(dec(CONTEXT))
        assertEquals("empty", c.status)
        assertEquals("minimax", c.model?.provider)
        assertEquals(512000L, c.model?.contextWindow)
    }

    @Test
    fun forkOptions() {
        val f = Wire.forkOptions(dec(FORK))
        assertEquals(false, f.canFork)
        assertEquals("message-boundary-not-found", f.unavailableReason)
        assertNotNull(f.suggestedTitle)
    }

    @Test
    fun backgroundTasksEmpty() {
        assertEquals(0, Wire.backgroundTasks(dec(BTASKS)).size)
    }

    @Test
    fun permissionListEmpty() {
        assertEquals(0, Wire.permissions(dec(PERMISSIONS)).size)
    }

    @Test
    fun skills() {
        val page = Wire.skills(dec(SKILLS))
        assertTrue(page.skills.isNotEmpty())
        assertTrue(page.skills.first().name.isNotBlank())
        assertTrue(page.skills.first().summary.isNotBlank())
    }

    @Test
    fun sessionSkillPolicy() {
        val s = Wire.session(
            """
            {
              "sessionId":"s1",
              "title":"Policy",
              "skillPolicy":{
                "closed":true,
                "mandatory":["pdf"],
                "optional":["docs"],
                "forbidden":["xlsx"]
              }
            }
            """.trimIndent()
        )!!
        val policy = s.skillPolicy!!
        assertEquals(true, policy.closed)
        assertEquals(listOf("pdf"), policy.mandatory)
        assertEquals(listOf("docs"), policy.optional)
        assertEquals(listOf("xlsx"), policy.forbidden)
        assertEquals("mandatory", policy.dispositionFor("PDF"))
        assertEquals("forbidden", policy.dispositionFor("xlsx"))
        assertEquals("hidden", policy.dispositionFor("unknown"))
    }

    @Test
    fun knowledgeProposalsAndReview() {
        val page = Wire.knowledgeProposals(
            """
            {
              "proposals":[
                {
                  "id":"kp_1",
                  "kind":"skill",
                  "action":"create",
                  "status":"pending",
                  "title":"PDF workflow",
                  "summary":"Capture the pdf flow",
                  "draft":"---\\nname: pdf\\n---\\n# PDF",
                  "sessionId":"s1"
                }
              ]
            }
            """.trimIndent()
        )
        assertEquals(1, page.proposals.size)
        val p = page.proposals.first()
        assertEquals("kp_1", p.id)
        assertEquals("skill", p.kind)
        assertEquals("create", p.action)
        assertEquals("PDF workflow", p.title)
        assertTrue(p.effectiveDraft.contains("pdf"))

        val bare = Wire.knowledgeProposals(
            """[{"id":"kp_2","kind":"memory","action":"improve","status":"pending","title":"Note","draft":"remember this"}]"""
        )
        assertEquals(1, bare.proposals.size)
        assertEquals("memory", bare.proposals.first().kind)

        val review = Wire.knowledgeReview("""{"applied":true,"title":"PDF workflow","status":"approved"}""")
        assertEquals(true, review.applied)
        assertEquals("approved", review.status)
        assertEquals("PDF workflow", review.title)
    }

    @Test
    fun modelRoster() {
        val models = Wire.models(dec(MODELS))
        assertTrue(models.isNotEmpty())
        val m = models.first()
        assertEquals("minimax", m.providerId)
        assertEquals(true, m.selected)
        assertEquals(512000L, m.contextLimit)
    }

    /** Every frame captured from a real POST /prompt stream decodes by type. */
    @Test
    fun streamFrames() {
        var heartbeat = 0
        var status = 0
        var messages = 0
        var generic = 0
        var done = 0
        val seen = mutableListOf<String>()
        for (i in FRAMES.indices) {
            val data = dec(FRAMES[i])
            when (val ev = Wire.streamEvent(FRAMES_TYPES[i], data)) {
                is StreamEvent.Heartbeat -> heartbeat++
                is StreamEvent.SessionStatus -> {
                    status++
                    seen.add("status=${ev.status}")
                }
                is StreamEvent.MessageUpsert -> {
                    messages++
                    assertEquals("user", ev.message.role)
                    assertEquals("Reply with exactly: PONG", ev.message.content)
                    assertNotNull(ev.message.id)
                }
                is StreamEvent.Generic -> generic++
                is StreamEvent.Done -> done++
                is StreamEvent.Failed -> seen.add("failed=${ev.message}")
                is StreamEvent.Unknown -> seen.add("unknown")
                else -> seen.add("other")
            }
        }
        assertEquals(1, heartbeat)
        assertEquals(1, status)
        assertEquals(1, messages)
        assertEquals(2, generic)
        assertEquals(1, done)
        assertTrue("expected an error status, saw $seen", seen.contains("status=error"))
    }

    /**
     * Message shape from the TuiMessage contract
     * (packages/tui/src/runtime/stream-events.ts): parts, tool calls with a
     * structured diff preview, token usage and fork/rewind affordances.
     */
    @Test
    fun assistantMessageWithDiffPreview() {
        val json = """
        {
          "type": "message",
          "message": {
            "id": "msg-assistant-1",
            "turnId": "turn_1",
            "role": "assistant",
            "kind": "final",
            "content": "Edited the file.",
            "thinking": "Need to patch the config.",
            "thinkingDurationMs": 412,
            "finishReason": "stop",
            "timestamp": 1790400000000,
            "usage": {"inputTokens": 1200, "outputTokens": 80, "cacheReadTokens": 900},
            "actions": {"fork": true, "rewind": true},
            "parts": [
              {"type": "thinking", "content": "Need to patch the config.", "durationMs": 412},
              {"type": "text", "content": "Edited the file."},
              {"type": "tool", "toolCall": {
                 "id": "call_1",
                 "name": "edit",
                 "status": "applied",
                 "durationMs": 37,
                 "input": {"path": "config.yaml"},
                 "output": {"ok": true},
                 "structuredPreview": {
                   "schemaVersion": 1,
                   "state": "applied",
                   "blocks": [
                     {"kind": "diff", "path": "config.yaml",
                      "diff": "--- a/config.yaml\n+++ b/config.yaml\n@@ -1 +1 @@\n-old\n+new\n",
                      "addedLines": 1, "removedLines": 1, "truncated": false}
                   ]
                 }
              }}
            ]
          }
        }
        """.trimIndent()

        val ev = Wire.streamEvent("message", json) as StreamEvent.MessageUpsert
        val m = ev.message
        assertEquals("assistant", m.role)
        assertEquals("final", m.kind)
        assertEquals(3, m.parts.size)
        assertTrue(m.parts[0] is MessagePart.Thinking)
        assertTrue(m.parts[1] is MessagePart.Text)
        val tool = (m.parts[2] as MessagePart.Tool).toolCall
        assertEquals("edit", tool.name)
        assertEquals("applied", tool.status)
        assertEquals(37L, tool.durationMs)
        val block = tool.preview!!.blocks.single() as PreviewBlock.Diff
        assertEquals("config.yaml", block.path)
        assertEquals(1, block.addedLines)
        assertEquals(1, block.removedLines)
        assertTrue(block.diff.contains("+new"))
        assertEquals(1200L, m.usage?.inputTokens)
        assertEquals(true, m.actions?.rewind)
        assertEquals("stop", m.finishReason)
    }

    /** `delta` chunks carry only the new text; the client appends them. */
    @Test
    fun deltaChunk() {
        val ev = Wire.streamEvent(
            "delta",
            """{"type":"delta","messageId":"m1","role":"assistant","content":"Hel","chunkIndex":0,"started":true}"""
        ) as StreamEvent.Delta
        assertEquals("m1", ev.messageId)
        assertEquals("Hel", ev.content)
        assertEquals(true, ev.started)
        assertEquals(false, ev.finish)
    }

    /** `session-status` carries `message` as a string, not an object. */
    @Test
    fun sessionStatusMessageIsText() {
        val ev = Wire.streamEvent(
            "session-status",
            """{"type":"session-status","status":"error","message":"no credentials"}"""
        ) as StreamEvent.SessionStatus
        assertEquals("error", ev.status)
        assertEquals("no credentials", ev.message)
    }

    @Test
    fun rewoundIds() {
        val ev = Wire.streamEvent(
            "messages-rewound",
            """{"type":"messages-rewound","messageIds":["a","b"]}"""
        ) as StreamEvent.MessagesRewound
        assertEquals(listOf("a", "b"), ev.messageIds)
    }

    @Test
    fun questionnaireRequest() {
        val json = """
        {
          "schemaVersion": 1,
          "id": "q_1",
          "title": "Pick a target",
          "steps": [
            {"id": "s1", "question": "Which env?", "selectionMode": 0, "allowOther": true,
             "otherPlaceholder": "Custom…", "required": true,
             "options": [{"id": "o1", "label": "staging", "recommended": true}]},
            {"id": "s2", "question": "Which checks?", "selectionMode": 1, "allowOther": false, "required": false,
             "options": [{"id": "c1", "label": "lint"}, {"id": "c2", "label": "tests"}]}
          ]
        }
        """.trimIndent()
        val q = Wire.questionnaire(Wire.root(json))!!
        assertEquals("q_1", q.id)
        assertEquals(2, q.steps.size)
        assertEquals("single", q.steps[0].selectionMode)
        assertEquals(true, q.steps[0].allowOther)
        assertEquals(true, q.steps[0].options[0].recommended)
        assertEquals("multiple", q.steps[1].selectionMode)
    }

    @Test
    fun permissionRequest() {
        val json = """
        {"requestId":"p1","agentName":"mavis","sessionId":"s1","toolName":"bash",
         "toolInput":"rm -rf build","toolDescription":"clean","reason":"destructive",
         "ruleContents":["allow bash"],"allowAlwaysSupported":true,
         "structuredPreview":{"state":"proposed","blocks":[{"kind":"summary","message":"2 files","reason":"binary"}]}}
        """.trimIndent()
        val p = Wire.permission(Wire.obj(json)!!)!!
        assertEquals("bash", p.toolName)
        assertEquals(true, p.allowAlwaysSupported)
        assertEquals(listOf("allow bash"), p.ruleContents)
        assertTrue(p.preview!!.blocks.single() is PreviewBlock.Summary)
    }

    @Test
    fun runtimeEventNames() {
        assertEquals("permission.ask", Wire.runtimeEventType("""{"type":"permission.ask"}"""))
        val (q, agent) = Wire.questionnaireFromEvent(
            """{"type":"questionnaire.ask","agentName":"mavis","request":{"id":"q9","steps":[]}}"""
        )!!
        assertEquals("q9", q?.id)
        assertEquals("mavis", agent)
        assertNull(Wire.permissionFromEvent("""{"type":"session.created"}"""))
    }
}
