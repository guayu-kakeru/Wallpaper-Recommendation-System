package com.wallpaperrecsys;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallpaperrecsys.datamanager.WallpaperDataManager;
import com.wallpaperrecsys.util.Config;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;

/**
 * WallpaperServer - lightweight HTTP server for wallpaper recommendation web UI
 * 壁纸推荐系统 Web 服务端
 */
public class WallpaperServer {

    private static final int DEFAULT_PORT = 6020;

    public static void main(String[] args) throws Exception {
        new WallpaperServer().run();
    }

    public void run() throws Exception {
        int basePort = DEFAULT_PORT;
        try {
            String portStr = System.getenv("PORT");
            if (portStr != null) {
                basePort = Integer.parseInt(portStr);
            }
        } catch (NumberFormatException ignored) {
        }

        // 1. 预先加载数据
        WallpaperDataManager.getInstance().loadData(
                Config.DEFAULT_WALLPAPER_DATA_PATH,
                Config.DEFAULT_RATING_DATA_PATH,
                Config.DEFAULT_WALLPAPER_EMB_PATH,
                Config.DEFAULT_USER_EMB_PATH
        );

        // 2. 找到 webroot 目录（classpath 下）
        URL webRootLocation = this.getClass().getResource("/webroot/index.html");
        if (webRootLocation == null) {
            throw new IllegalStateException("Unable to determine webroot URL location");
        }

        URI webRootUri = URI.create(
                webRootLocation.toURI().toASCIIString().replaceFirst("/index.html$", "/")
        );
        System.out.printf("Web Root URI: %s%n", webRootUri.getPath());

        // 3. 尝试多个端口，避免端口被占用需要手动设置
        final int maxAttempts = 5;
        Exception lastException = null;

        for (int i = 0; i < maxAttempts; i++) {
            int port = basePort + i;
            try {
                InetSocketAddress inetAddress = new InetSocketAddress("0.0.0.0", port);
                Server server = new Server(inetAddress);

                // 创建上下文，既服务静态页面，又挂载 API
                ServletContextHandler context = new ServletContextHandler();
                context.setContextPath("/");
                context.setBaseResource(Resource.newResource(webRootUri));
                context.setWelcomeFiles(new String[]{"index.html"});

                // 静态文件
                context.addServlet(DefaultServlet.class, "/");

                // API Servlet
                context.addServlet(new ServletHolder(new ApiServlet()), "/api/*");

                server.setHandler(context);

                System.out.println("Wallpaper RecSys Web Server started on port " + port);
                server.start();
                server.join();
                return;
            } catch (java.net.BindException be) {
                lastException = be;
                System.err.println("Port " + port + " is already in use, trying port " + (port + 1) + "...");
            }
        }

        // 如果多次尝试仍失败，抛出最后一次异常
        if (lastException != null) {
            throw lastException;
        }
    }

    /**
     * Simple API servlet that routes wallpaper recommendation/search requests.
     * 简单 API Servlet，提供推荐与搜索接口
     */
    public static class ApiServlet extends HttpServlet {

        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setCharacterEncoding("UTF-8");
            resp.setContentType("application/json;charset=UTF-8");

            String path = req.getPathInfo();
            if (path == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            try {
                switch (path) {
                    case "/rec/personal":
                        handlePersonalRec(req, resp);
                        break;
                    case "/rec/similar":
                        handleSimilarRec(req, resp);
                        break;
                    case "/search":
                        handleSearch(req, resp);
                        break;
                    case "/rec/scenario":
                        handleScenarioRec(req, resp);
                        break;
                    case "/rec/time":
                        handleTimeRec(req, resp);
                        break;
                    default:
                        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                }
            } catch (Exception e) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                mapper.writeValue(resp.getWriter(), "Error: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void handlePersonalRec(HttpServletRequest req, HttpServletResponse resp) throws Exception {
            String userIdStr = req.getParameter("userId");
            String sizeStr = req.getParameter("size");
            String model = req.getParameter("model");
            String pageStr = req.getParameter("page");

            int userId = userIdStr != null ? Integer.parseInt(userIdStr) : 1;
            int size = sizeStr != null ? Integer.parseInt(sizeStr) : 20;
            if (model == null || model.isEmpty()) {
                model = "emb";
            }
            int page = pageStr != null ? Integer.parseInt(pageStr) : 0;
            if (page < 0) page = 0;

            java.util.List<com.wallpaperrecsys.datamanager.Wallpaper> list =
                    com.wallpaperrecsys.recprocess.RecForYouProcess.getRecList(userId, Math.min(size * (page + 1), 5000), model);
            mapper.writeValue(resp.getWriter(), paginate(list, size, page));
        }

        private void handleSimilarRec(HttpServletRequest req, HttpServletResponse resp) throws Exception {
            String wallpaperIdStr = req.getParameter("wallpaperId");
            String sizeStr = req.getParameter("size");
            String model = req.getParameter("model");

            if (wallpaperIdStr == null) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                mapper.writeValue(resp.getWriter(), "wallpaperId is required");
                return;
            }

            int wallpaperId = Integer.parseInt(wallpaperIdStr);
            int size = sizeStr != null ? Integer.parseInt(sizeStr) : 20;
            if (model == null || model.isEmpty()) {
                model = "emb";
            }

            java.util.List<com.wallpaperrecsys.datamanager.Wallpaper> list =
                    com.wallpaperrecsys.recprocess.SimilarWallpaperProcess.getRecList(wallpaperId, size, model);
            mapper.writeValue(resp.getWriter(), list);
        }

        private void handleSearch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
            String q = req.getParameter("q");
            String sizeStr = req.getParameter("size");
            int size = sizeStr != null ? Integer.parseInt(sizeStr) : 30;

            java.util.List<com.wallpaperrecsys.datamanager.Wallpaper> list =
                    com.wallpaperrecsys.service.AISearchService.intelligentSearch(q, size);
            mapper.writeValue(resp.getWriter(), list);
        }

        private void handleScenarioRec(HttpServletRequest req, HttpServletResponse resp) throws Exception {
            String scene = req.getParameter("scene");
            String userIdStr = req.getParameter("userId");
            String sizeStr = req.getParameter("size");
            String pageStr = req.getParameter("page");

            int userId = userIdStr != null ? Integer.parseInt(userIdStr) : 1;
            int size = sizeStr != null ? Integer.parseInt(sizeStr) : 20;
            if (scene == null || scene.isEmpty()) {
                scene = "work";
            }
            int page = pageStr != null ? Integer.parseInt(pageStr) : 0;
            if (page < 0) page = 0;

            java.util.List<com.wallpaperrecsys.datamanager.Wallpaper> list =
                    com.wallpaperrecsys.recprocess.ScenarioBasedRecommendation.recommendByScenario(
                            scene, userId, Math.min(size * (page + 1), 5000));
            mapper.writeValue(resp.getWriter(), paginate(list, size, page));
        }

        private void handleTimeRec(HttpServletRequest req, HttpServletResponse resp) throws Exception {
            String userIdStr = req.getParameter("userId");
            String sizeStr = req.getParameter("size");
            String pageStr = req.getParameter("page");

            int userId = userIdStr != null ? Integer.parseInt(userIdStr) : 1;
            int size = sizeStr != null ? Integer.parseInt(sizeStr) : 20;
            int page = pageStr != null ? Integer.parseInt(pageStr) : 0;
            if (page < 0) page = 0;

            java.util.List<com.wallpaperrecsys.datamanager.Wallpaper> list =
                    com.wallpaperrecsys.recprocess.TimeAwareRecommendation.recommendByTime(userId, Math.min(size * (page + 1), 5000));
            mapper.writeValue(resp.getWriter(), paginate(list, size, page));
        }

        /**
         * Simple paging: return page-th chunk of size.
         * 若 page 超出范围则回到第 0 页，配合“换一批”循环使用。
         */
        private java.util.List<com.wallpaperrecsys.datamanager.Wallpaper> paginate(
                java.util.List<com.wallpaperrecsys.datamanager.Wallpaper> list,
                int size,
                int page) {
            if (list == null) return java.util.Collections.emptyList();
            if (size <= 0) return java.util.Collections.emptyList();
            int start = page * size;
            if (start >= list.size()) {
                start = 0;
            }
            int end = Math.min(start + size, list.size());
            return list.subList(start, end);
        }
    }
}


