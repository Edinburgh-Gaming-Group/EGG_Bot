package eggbot;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.Random;

import javax.imageio.ImageIO;

public class Utils {

	public static Color randomColor() {
		Random random = new Random();
		int r = random.nextInt(200) + 55;
		int g = random.nextInt(200) + 55;
		int b = random.nextInt(200) + 55;
		return new Color(r, g, b);
	}
	
}
