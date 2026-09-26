package com.lingrui.router;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;

public class SlowUpstreamServer {

    public static void main(String[] args) throws Exception{
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 18081), 0);

        server.createContext("/chat/completions", exchange -> {
            try {
                //读完请求体，但不打印其中内容
                exchange.getRequestBody().readAllBytes();
                System.out.println("收到请求，故意等待 70 秒");

                // 模拟上游迟迟不返回响应
                Thread.sleep(70_000);

                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException exception){
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });

        server.start();
        System.out.println("测试服务已启动：http://127.0.0.1:18081");
    }
}
