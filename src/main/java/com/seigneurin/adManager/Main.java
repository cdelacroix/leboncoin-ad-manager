package com.seigneurin.adManager;

import org.junit.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static Logger logger = Logger.getLogger("com.seigneurin.adManager");

    private static String action;
    private static String pathname;

    private static SellerSettings sellerSettings;
    private static ObjectSettings objectSettings;
    private static WebDriver driver;

    public static void main(String[] args) throws Exception {
        Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.OFF);

        parseArguments(args);
        loadConfiguration();
        
    	driver = new FirefoxDriver();
    	driver.manage().timeouts().implicitlyWait(5, TimeUnit.SECONDS); // manage the wait
    	
        if ("unpublish".equals(action) || "republish".equals(action)) {
            deleteAd();
        }
        if ("publish".equals(action) || "republish".equals(action)) {
            postAd();
        }
        
        driver.quit();
    }

    private static void parseArguments(String[] args) {
        if (args.length < 2)
            printUsageAndExit("Missing parameter.");
        if (args.length > 2)
            printUsageAndExit("Too many parameters.");

        action = args[0];
        if ("publish".equals(action) == false && "unpublish".equals(action) == false && "republish".equals(action) == false)
            printUsageAndExit("Invalid ACTION.");

        pathname = args[1];
    }

    private static void printUsageAndExit(String reason) {
        if (reason != null) {
            System.out.println(reason);
            System.out.println();
        }

        System.out.println("Usage: ACTION PATH");
        System.out.println("  ACTION: publish, republish");
        System.out.println("  PATH: path to directory containing a settings.yaml file and a JPG photo.");
        System.exit(1);
    }

    private static void loadAccountPage() {
        // load the home page

        logger.log(Level.INFO, "Chargement de la page d'accueil...");
        driver.get("http://www.leboncoin.fr/");
        Assert.assertEquals("Petites annonces gratuites d'occasion - leboncoin.fr", driver.getTitle());

        // click the link to the region
        logger.log(Level.INFO, "Sélection de la région...");
        driver.findElement(By.xpath("//a[@title='" + sellerSettings.region + "']")).click();
        WebElement submitElement = driver.findElement(By.xpath("//input[@type='submit']")); // small kludge: wait for page load after click()
        Assert.assertEquals("Annonces " + sellerSettings.region + " - leboncoin.fr", driver.getTitle());

        // fill-in the login form
        logger.log(Level.INFO, "Connexion au compte utilisateur...");
        driver.findElement(By.name("st_username")).sendKeys(sellerSettings.email);
        driver.findElement(By.name("st_passwd")).sendKeys(sellerSettings.password);
        submitElement.click();
        driver.findElement(By.id("account_login")); // wait for page load after click()
        Assert.assertEquals("Compte", driver.getTitle());
    }

    private static void postAd() {
        loadAccountPage();

        // click the link to the page to post an ad
        logger.log(Level.INFO, "Chargement de la page de dépôt d'annonces...");
        driver.findElement(By.xpath("//a[text()='Déposez une annonce']")).click();
        WebElement form = driver.findElement(By.name("formular")); // wait after click()
        Assert.assertEquals("Formulaire de dépôt de petites annonces gratuites sur Leboncoin.fr", driver.getTitle());

        // fill-in the form
        logger.log(Level.INFO, "Remplissage des champs 'Localisation'...");
        selectOption(form.findElement(By.name("region")), sellerSettings.region);
        selectOption(form.findElement(By.name("dpt_code")), sellerSettings.departement);
        form.findElement(By.name("zipcode")).sendKeys(sellerSettings.zipCode);

        logger.log(Level.INFO, "Remplissage des champs 'Catégorie'...");
        selectOption(form.findElement(By.name("category")), objectSettings.category);

        logger.log(Level.INFO, "Remplissage des champs 'Vos informations'...");
        //form.findElement(By.name("name")).sendKeys(sellerSettings.name);
        //form.findElement(By.name("email")).sendKeys(sellerSettings.email);
        //form.findElement(By.name("phone")).sendKeys(sellerSettings.phoneNumber);
        
        WebElement phoneHiddenElement = form.findElement(By.name("phone_hidden"));
        if(!phoneHiddenElement.isSelected()) {
            phoneHiddenElement.click();
        }

        logger.log(Level.INFO, "Remplissage des champs 'Votre annonce'...");
        form.findElement(By.name("subject")).sendKeys(objectSettings.subject);
        form.findElement(By.name("body")).sendKeys(objectSettings.body);
        form.findElement(By.name("price")).sendKeys(objectSettings.price);

        int nbImages = java.lang.Math.min(3, objectSettings.imageFiles.length);
        for(int i = 0; i < nbImages; i++) {
        	WebElement imageInput = driver.findElement(By.id("image" + i));
            String imagePath = objectSettings.imageFiles[i].getAbsolutePath();
        	imageInput.sendKeys(imagePath);
        }

        logger.log(Level.INFO, "Validation...");
        driver.findElement(By.name("validate")).click(); // not reachable via form anymore
        List<WebElement> errorsElements = driver.findElements(By.xpath("//span[@class='error']"));
        for (WebElement errorElement : errorsElements) {
            if (!errorElement.getText().isEmpty())
                System.err.println("Error: " + errorElement.getText());
        }

        Assert.assertEquals("Vérifiez votre annonce.", driver.getTitle());
        
        for(int i = 0; i < nbImages; i++) {
        	String text = i > 0 ? "Photo " + (i + 1) : "Photo principale";
            WebElement photoElement = driver.findElement(By.xpath("//div[text()='" + text + "']"));
            Assert.assertNotNull("Photo " + (i + 1) + " was not uploaded", photoElement);
        }

        form = driver.findElement(By.name("formular"));

        logger.log(Level.INFO, "Remplissage des champs 'Vérifiez le contenu de votre annonce'...");

     	WebElement element = form.findElement(By.name("city"));
    	if(element.isDisplayed()) {
    		selectOption(form.findElement(By.name("city")), sellerSettings.city);
    	}
        
    	form.findElement(By.name("accept_rule")).click();
    	form.findElement(By.name("create")).click();

        Assert.assertTrue(driver.getPageSource().contains("Votre annonce a été envoyée à notre équipe éditoriale."));
        Assert.assertEquals("Confirmation d'envoi de votre annonce", driver.getTitle());
        
        logger.log(Level.INFO, "Terminé !");
    }

    private static void deleteAd() {
        loadAccountPage();

        logger.log(Level.INFO, "Chargement de la page de l'objet...");
        driver.findElement(By.linkText(objectSettings.subject)).click();
        WebElement deleteElement = driver.findElement(By.linkText("Supprimer"));
        Assert.assertTrue("Unexpected page: " + driver.getTitle(), driver.getTitle().startsWith(objectSettings.subject));

        logger.log(Level.INFO, "Chargement de la page de suppression de l'objet...");
        deleteElement.click();
        WebElement continueElement = driver.findElement(By.xpath("//input[@id='store__continue']")); // wait for page load after click()
        Assert.assertEquals("Leboncoin.fr - Gestion de l'annonce", driver.getTitle());

        logger.log(Level.INFO, "Suppression de l'objet...");
        continueElement.click();
        WebElement validateButton = driver.findElement(By.id("st_ads_continue"));
        Assert.assertEquals("Confirmation de votre suppression", driver.getTitle());

        logger.log(Level.INFO, "Validation...");
        validateButton.click();
        Assert.assertTrue(driver.getPageSource().contains("Votre demande de suppression"));
        Assert.assertTrue(driver.getPageSource().contains("bien été prise en compte."));
    }

    private static void loadConfiguration() throws FileNotFoundException {
        Yaml yaml = new Yaml();

        File path = new File(pathname);

        String sellerYamlFilename = path.getParent() + File.separator + "settings.yaml";
        logger.log(Level.INFO, "Chargement des propriétés du vendeur : " + sellerYamlFilename);
        FileInputStream mainYamlFileStream = new FileInputStream(sellerYamlFilename);
        sellerSettings = yaml.loadAs(mainYamlFileStream, SellerSettings.class);

        String objectYamlFilename = path + File.separator + "settings.yaml";
        logger.log(Level.INFO, "Chargement des propriétés de l'objet : " + objectYamlFilename);
        FileInputStream objectYamlFileStream = new FileInputStream(objectYamlFilename);
        objectSettings = yaml.loadAs(objectYamlFileStream, ObjectSettings.class);

        objectSettings.imageFiles = path.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".png");
            }
        });
    }

    private static void selectOption(WebElement selectElement, String optionText) {
        Assert.assertNotNull(selectElement);
        WebElement regionOption = findOption(selectElement, optionText);
        regionOption.click();
    }

    private static WebElement findOption(WebElement selectElement, String optionText) {
        for (WebElement option : selectElement.findElements(By.tagName("option")))
            if (optionText.equals(option.getText()))
                return option;
        Assert.fail("No option with text: " + optionText);
        return null;
    }

}
