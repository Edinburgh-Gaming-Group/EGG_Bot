package eggbot;

import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import reactor.core.publisher.Mono;

public class EggBot {

	public static DiscordClient CLIENT;

	public static void main(String[] args) {
		try {
			if (args.length == 0)
				throw new IllegalArgumentException("Please enter a client key.");
			AnnotationListener listener = new AnnotationListener();

			CLIENT = new DiscordClientBuilder(args[0]).build();
			CLIENT.getEventDispatcher().on(ReadyEvent.class).flatMap(ready -> {
				listener.onReady(ready);
				return Mono.empty();
			}).subscribe();

			CLIENT.getEventDispatcher().on(MessageCreateEvent.class).flatMap(
					event -> Mono.fromRunnable(() -> listener.onMessage(event)).onErrorResume(t -> Mono.empty()))
					.subscribe();
			CLIENT.login().block();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
