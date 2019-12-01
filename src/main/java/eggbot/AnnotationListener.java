package eggbot;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;

import discord4j.core.DiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.PermissionOverwrite;
import discord4j.core.object.entity.Channel;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.PrivateChannel;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.TextChannel;
import discord4j.core.object.entity.User;
import discord4j.core.object.util.Image;
import discord4j.core.object.util.Permission;
import discord4j.core.object.util.PermissionSet;
import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Mono;

public class AnnotationListener {

	private static Logger log = Logger.getGlobal();
	private long CONSOLE = 650368452531322890L;
	private long EVENTS_CATEGORY = 648531080751742976L;

	private Guild guild;
	private ArrayList<String> parsedData = new ArrayList<>();

	private HashMap<String, Long> eventRoles = new HashMap<>();
	private PermissionSet readSend = PermissionSet.of(Permission.VIEW_CHANNEL, Permission.SEND_MESSAGES,
			Permission.READ_MESSAGE_HISTORY);

	public void onReady(ReadyEvent event) {
		DiscordClient cli = event.getClient();
		cli.edit(spec -> {
			spec.setAvatar(Image.ofUrl(
					"https://upload.wikimedia.org/wikipedia/commons/thumb/1/10/Flag_of_Scotland.svg/2560px-Flag_of_Scotland.svg.png")
					.block());
			spec.setUsername("Events Bot");
		}).block();
		guild = cli.getGuilds().blockFirst();
		readEventRoles();
	}

	private void readEventRoles() {
		try {
			List<String> lines = FileUtils.readLines(new File("events"));
			for (String line : lines) {
				String[] data = line.split(" ");
				eventRoles.put(data[0], new Long(data[1]));
			}
		} catch (IOException e) {
			log.warning("Failed to read events to file");
		}
	}

	private void saveEventRoles() {
		ArrayList<String> lines = new ArrayList<>();
		for (Map.Entry<String, Long> entry : eventRoles.entrySet()) {
			lines.add(entry.getKey() + " " + entry.getValue());
		}
		try {
			FileUtils.writeLines(new File("events"), lines);
		} catch (IOException e) {
			log.warning("Failed to save events to file");
		}
	}

	public void onMessage(MessageCreateEvent event) {
		log.info("MessageCreateEvent fired");

		Message message = event.getMessage();
		Channel chn = message.getChannel().block();
		String msg = message.getContent().orElse("");
		User usr = message.getAuthor().get();

		if (usr.asMember(guild.getId()).block().isBot())
			return;

		if (chn.getId().asLong() == CONSOLE) {
			parseEventCreation(msg, (TextChannel) chn);
		} else {
			addUserToRoleIfMatch(usr, msg);
		}
	}

	private void parseEventCreation(String message, TextChannel chn) {
		parsedData.add(message.toLowerCase().replaceAll(" ", "-"));
		if (parsedData.size() == 1) {
			chn.createMessage("I shall create an event channel called: " + parsedData.get(0)
					+ ". Now what is the event description?").block();
		} else if (parsedData.size() == 2) {
			chn.createMessage(
					"Cool. Now, type the word people will need to add themselves to the event. Do not include '!', that is handled automatically.")
					.block();
		} else if (parsedData.size() == 3) {
			createEvent();
		}
	}

	private void addUserToRoleIfMatch(User user, String message) {
		Long roleId = parseRoleIdFromText(message);
		Member member = user.asMember(guild.getId()).block();
		if (roleId != null) {
			log.info("Attempting to add " + user.getUsername() + " to role " + roleId);
			if (checkIfRoleIsAvailable(member, roleId)) {
				PrivateChannel priv = member.getPrivateChannel().block();
				priv.createMessage("Hi " + user.getMention() + ", You've been added to this event!").block();
				member.addRole(Snowflake.of(roleId.longValue())).block();
			} else {
				log.info("User has already been added to this role, or it doesn't exist.");
			}
		}
	}

	private Long parseRoleIdFromText(String message) {
		for (String msg : message.split(" ")) {
			Long roleId = eventRoles.get(message);
			if (roleId != null)
				return roleId;
		}
		return null;
	}

	private boolean checkIfRoleIsAvailable(Member member, long roleId) {

		// Check to see if this user has this role
		for (Snowflake role : member.getRoleIds()) {
			if (role.asLong() == roleId)
				return false;
		}

		// Check to see if the role actually exists
		for (Snowflake role : guild.getRoleIds()) {
			if (role.asLong() == roleId)
				return true;
		}

		return false;
	}

	private Role createRole(String name) {
		return guild.createRole(r -> {
			r.setColor(Utils.randomColor());
			r.setName(convertChannelNameToRoleName(name));
			r.setPermissions(readSend);
		}).block();
	}

	private String convertChannelNameToRoleName(String channelName) {
		String[] data = channelName.split("-");
		String roleName = "";
		for (String datum : data) {
			roleName += datum.substring(0, 1).toUpperCase() + datum.substring(1) + " ";
		}
		return roleName;
	}

	private void createEvent() {
		Role everyone = guild.getEveryoneRole().block();
		Role eventRole = createRole(parsedData.get(0));

		HashSet<PermissionOverwrite> permissions = new HashSet<>();
		permissions.add(PermissionOverwrite.forRole(eventRole.getId(), readSend, PermissionSet.none()));
		permissions.add(PermissionOverwrite.forRole(everyone.getId(), PermissionSet.none(), readSend));

		guild.createTextChannel(chn -> {
			chn.setParentId(Snowflake.of(EVENTS_CATEGORY));
			chn.setName(parsedData.get(0));
			chn.setTopic(parsedData.get(1));
			chn.setPosition(2);
			chn.setPermissionOverwrites(permissions);
		}).block();

		eventRoles.put("!" + parsedData.get(2), new Long(eventRole.getId().asLong()));

		saveEventRoles();
		parsedData.clear();
	}

}
