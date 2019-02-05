/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.creator;

import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import org.aeonbits.owner.Config.DefaultValue;
import org.aeonbits.owner.Config.Description;
import org.aeonbits.owner.Config.GroupOrder;
import org.aeonbits.owner.Config.Key;
import org.aeonbits.owner.Config.NoProperty;
import org.aeonbits.owner.Config.ValorizedAs;

/**
 * PropertiesFileCreator helps you to automate the process of properties creation.
 * 
 * @author Luca Taddeo
 */
public class PropertiesFileCreator implements Creator {

    public String header = "#\n"
            + "#\n"
            + "# Properties file created for\n";

    public String footer = "\n\n\n# Properties file autogenerated by OWNER :: PropertyCreator\n"
            + "# Created [%s] in %s ms\n";

    public long lastExecutionTime = 0;
    
    /**
     * Method to parse the class and write file in the choosen output.
     *
     * @param clazz class to parse
     * @param output output file path
     * @param headerName
     * @param projectName
     *
     * @return if the file was written correctly.
     * @throws Exception
     */
    public boolean parse(Class clazz, String output, String headerName, String projectName) throws Exception {
        boolean valid = true;
        long startTime = System.currentTimeMillis();
        
        Group[] groups = parseMethods(clazz);
        long finishTime = System.currentTimeMillis();

        lastExecutionTime = finishTime - startTime;
        
        String result = toPropertiesString(groups, headerName, projectName);

        valid = writeProperties(output, result);

        return valid;
    }

    /**
     * Method to get group array with subgroups and properties.
     *
     * @param clazz class to parse
     * @return array of groups
     */
    private Group[] parseMethods(Class clazz) {
        List<Group> groups = new ArrayList();
        Group unknownGroup = new Group();
        unknownGroup.title = "GENERIC PROPERTIES";
        groups.add(unknownGroup);
        String[] groupsOrder = new String[0];

        // Retrieve the groups order if there is the annotation
        if (clazz.isAnnotationPresent(GroupOrder.class)) {
            GroupOrder order = (GroupOrder) clazz.getAnnotation(GroupOrder.class);

            groupsOrder = order.value();
        }

        try {
            for (Method method : clazz.getMethods()) {
                Property prop = new Property();

                prop.deprecated = method.isAnnotationPresent(Deprecated.class);

                prop.addToPropertyFile = !method.isAnnotationPresent(NoProperty.class);

                if (method.isAnnotationPresent(Key.class)) {
                    Key val = method.getAnnotation(Key.class);
                    prop.name = val.value();
                } else {
                    prop.name = method.getName();
                }

                if (method.isAnnotationPresent(DefaultValue.class)) {
                    DefaultValue val = method.getAnnotation(DefaultValue.class);
                    prop.defaultValue = val.value();
                }

                if (method.isAnnotationPresent(ValorizedAs.class)) {
                    ValorizedAs val = method.getAnnotation(ValorizedAs.class);
                    prop.valorizedAs = val.value();
                }

                if (method.isAnnotationPresent(Description.class)) {
                    Description val = method.getAnnotation(Description.class);
                    prop.description = val.value();
                }

                if (method.isAnnotationPresent(org.aeonbits.owner.Config.Group.class)) {
                    org.aeonbits.owner.Config.Group group = method.getAnnotation(org.aeonbits.owner.Config.Group.class);
                    Group currentGroup = getOrAddGroup(group.value(), groups);

                    currentGroup.properties.add(prop);
                } else {
                    unknownGroup.properties.add(prop);
                }
            }
        } catch (Throwable ex) {
            throw new RuntimeException("Something wrong happened during class conversion", ex);
        }

        return orderGroup(groups, groupsOrder);
    }

    /**
     * Order groups based on passed order.
     *
     * @param groups groups to order
     * @param groupsOrder order to follow
     * @return ordered groups
     */
    private Group[] orderGroup(List<Group> groups, String[] groupsOrder) {
        LinkedList<Group> groupsOrdered = new LinkedList();

        List<Group> remained = new ArrayList(groups);

        for (String order : groupsOrder) {
            for (Group remain : remained) {
                if (remain.title.equals(order)) {
                    groupsOrdered.add(remain);
                    remained.remove(remain);
                    break;
                }
            }
        }

        groupsOrdered.addAll(remained);

        return groupsOrdered.toArray(new Group[groupsOrdered.size()]);
    }

    /**
     * Get specific group from the list or ad new one if there isn't the choosen
     * group.
     *
     * @param groupsName group to find
     * @param groups list to search for
     * @return Found/added groups
     */
    private Group getOrAddGroup(String[] groupsName, List<Group> groups) {
        List<Group> currentLevel = groups;
        Group lastFound = null;
        for (String groupName : groupsName) {
            Group found = null;
            for (Group current : currentLevel) {
                if (groupName.equals(current.title)) {
                    found = current;
                    break;
                }
            }

            if (found != null) {
                currentLevel = found.subGroups;
                lastFound = found;
            } else {
                lastFound = new Group();
                lastFound.title = groupName;
                currentLevel.add(lastFound);
                currentLevel = lastFound.subGroups;
            }
        }

        return lastFound;
    }

    /**
     * Convert groups list into string.
     *
     * @param groups
     * @return
     */
    private String toPropertiesString(Group[] groups, String headerName, String projectName) {
        StringBuilder result = new StringBuilder();
        
        // Append header
        result.append(generateFileHeader(headerName, projectName));
        
        // Append properties
        for (Group group : groups) {
            result.append(group.toString());
        }

        // Append footer
        result.append(generateFileFooter());
        
        return result.toString();
    }


    /**
     * Generate header for the file.
     * 
     * @param headerName
     * @param projectName
     * @return 
     */
    private String generateFileHeader(String headerName, String projectName) {
        String headerAscii = "";
        String projectNameTemp = "# " + projectName + "\n"
                + "#\n\n";

        if (headerName != null && !headerName.isEmpty()) {
            headerAscii = createAscii(headerName) + "\n";
        }

        return String.format(headerAscii + header + projectNameTemp);
    }

        
    /**
     * Generate footer for the file.
     * 
     * @return 
     */
    private String generateFileFooter() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
	Date date = new Date();
        String dateString = dateFormat.format(date);
        return String.format(footer, dateString, lastExecutionTime);
    }
    
    /**
     * Write string to file.
     *
     * @param output output file
     * @param propertiesString string to write
     * @return
     */
    private boolean writeProperties(String output, String propertiesString) {
        boolean written = false;
        PrintWriter out = null;
        try {
            out = new PrintWriter(output);
            out.println(propertiesString);
            written = true;
        } catch (Throwable ex) {
            throw new RuntimeException("Output File problems", ex);
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (Throwable ex) {
            }
        }
        return written;
    }

    /**
     * Create an ascii graphic string based on passed text.
     *
     * @param text
     * @return
     */
    public static String createAscii(String text) {
        StringBuilder finalStr = new StringBuilder();
        int width = 100;
        int height = 30;

        //BufferedImage image = ImageIO.read(new File("/Users/mkyong/Desktop/logo.jpg"));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics g = image.getGraphics();
        g.setFont(new Font("SansSerif", Font.BOLD, 15));

        Graphics2D graphics = (Graphics2D) g;
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.drawString(text, 0, 20);

        for (int y = 0; y < height; y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < width; x++) {
                sb.append(image.getRGB(x, y) == -16777216 ? " " : "$");
            }

            if (sb.toString().trim().isEmpty()) {
                continue;
            }

            finalStr.append(sb.insert(0, "# ").toString().trim()).append("\n");
        }

        return finalStr.toString().trim();
    }
}

class Group {

    public String title = "";
    public List<Group> subGroups = new ArrayList();
    public List<Property> properties = new ArrayList();

    public String toString(boolean subHeader) {
        StringBuilder group = new StringBuilder();

        if (subHeader) {
            group.append("#----------------\n")
                    .append("# - ").append(title).append(" -\n")
                    .append("#----------------\n\n");
        } else {
            group.append("#//----------------------------------------------------------\n")
                    .append("#// ").append(title).append("\n")
                    .append("#//----------------------------------------------------------\n\n");
        }

        for (Property property : properties) {
            if (property.addToPropertyFile) {
                group.append(property.toString());
            }
        }

        for (Group current : subGroups) {
            group.append(current.toString(true));
        }

        return group.toString();
    }

    @Override
    public String toString() {
        return toString(false);
    }
}

class Property {

    public String name = "";
    public String defaultValue = "";
    public String valorizedAs = "";
    public String description = "";
    public boolean addToPropertyFile = true;
    public boolean deprecated = false;

    @Override
    public String toString() {
        StringBuilder property = new StringBuilder();

        property.append("# \n");

        if (deprecated) {
            property.append("# DEPRECATED PROPERTY\n")
                    .append("# \n");
        }

        String[] descriptionLines = description.split("\n");

        for (String line : descriptionLines) {
            property.append("# ").append(line).append("\n");
        }

        property.append("# \n")
                .append("# Default(\"").append(defaultValue).append("\")\n");

        if (valorizedAs != null && !valorizedAs.isEmpty()) {
            property.append(name).append("=").append(valorizedAs);
        } else {
            property.append("#").append(name).append("=").append(defaultValue);
        }

        property.append("\n\n");

        return property.toString();
    }
}