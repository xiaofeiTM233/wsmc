package wsmc.paper;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import wsmc.WSMC;
import wsmc.HttpGetSniffer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public class WSMCPaperPlugin extends JavaPlugin {

	@Override
	public void onEnable() {
		try {
			Object craftServer = Bukkit.getServer();
			Object minecraftServer = craftServer.getClass().getMethod("getServer").invoke(craftServer);

			Method getConnection = minecraftServer.getClass().getMethod("getConnection");
			Object serverConnection = getConnection.invoke(minecraftServer);

			Field channelsField = serverConnection.getClass().getDeclaredField("channels");
			channelsField.setAccessible(true);
			@SuppressWarnings("unchecked")
			List<ChannelFuture> channels = (List<ChannelFuture>) channelsField.get(serverConnection);

			for (ChannelFuture future : channels) {
				Channel serverChannel = future.channel();

				String acceptorName = null;
				for (Map.Entry<String, ChannelHandler> entry : serverChannel.pipeline()) {
					if (entry.getValue().getClass().getName().contains("ServerBootstrapAcceptor")) {
						acceptorName = entry.getKey();
						break;
					}
				}

				if (acceptorName == null) {
					getLogger().warning("WSMC: ServerBootstrapAcceptor not found in server channel pipeline");
					continue;
				}

				serverChannel.pipeline().addBefore(acceptorName, "wsmc-child-injector",
					new ChannelInboundHandlerAdapter() {
						@Override
						public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
							ctx.fireChannelRead(msg);
							if (msg instanceof Channel) {
								Channel child = (Channel) msg;
								child.pipeline().addFirst("wsmc-http-sniffer", new HttpGetSniffer(null));
							}
						}

						@Override
						public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
							getLogger().warning("WSMC child injector error: " + cause.getMessage());
							ctx.fireExceptionCaught(cause);
						}
					});
			}

			getLogger().info("WSMC Paper plugin enabled. WebSocket connections accepted on port " +
				Bukkit.getPort() + ".");
		} catch (Exception e) {
			getLogger().severe("WSMC Paper failed to register channel handlers.");
			e.printStackTrace();
		}
	}
}
