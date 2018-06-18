package com.gemalto.chaos.container;

import com.gemalto.chaos.attack.Attack;
import com.gemalto.chaos.attack.enums.AttackType;
import com.gemalto.chaos.container.enums.ContainerHealth;
import com.gemalto.chaos.fateengine.FateManager;
import com.gemalto.chaos.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

public abstract class Container {

    protected static List<AttackType> supportedAttackTypes = new ArrayList<>();
    protected final Logger log = LoggerFactory.getLogger(getClass());
    private ContainerHealth containerHealth;
    @Autowired
    private FateManager fateManager;

    protected abstract Platform getPlatform();

    public boolean supportsAttackType(AttackType attackType) {
        return supportedAttackTypes != null && supportedAttackTypes.contains(attackType);
    }

    protected void updateContainerHealth() {
        containerHealth = getPlatform().getHealth(this);
    }

    public ContainerHealth getContainerHealth() {
        updateContainerHealth();
        return containerHealth;
    }

    public void setContainerHealth(ContainerHealth containerHealth) {
        this.containerHealth = containerHealth;
    }

    public Attack createAttack() {
        return createAttack(
                supportedAttackTypes
                        .get(new Random()
                                .nextInt(supportedAttackTypes.size())
                        )
        );
    }

    public abstract Attack createAttack(AttackType attackType);

    public boolean canDestroy() {
        return fateManager.getFateEngineForContainer(this).canDestroy();
    }

    public void attackContainer(AttackType attackType) {
        setContainerHealth(ContainerHealth.UNDER_ATTACK);
        log.info("Starting a {} attack against container {}", attackType, this);
        switch (attackType) {
            case STATE:
                attackContainerState();
                break;
            case NETWORK:
                attackContainerNetwork();
                break;
            case RESOURCE:
                attackContainerResources();
                break;
        }
    }

    public abstract void attackContainerState();

    public abstract void attackContainerResources();

    public abstract void attackContainerNetwork();

    /**
     * Uses all the fields in the container implementation (but not the Container parent class)
     * to create a checksum of the container. This checksum should be immutable and can be used
     * to recognize when building a roster if the container already exists, and can reference
     * the same object.
     *
     * @return A checksum (format long) of the class based on the implementation specific fields
     */
    long getIdentity() {
        ArrayList<Byte> byteArray = new ArrayList<>();
        for (Field field : this.getClass().getDeclaredFields()) {
            byte fieldByte = 0;
            try {
                fieldByte = field.getByte(this);
            } catch (IllegalAccessException e) {
                log.error("Failed to load bytes for field {}", field, e);
            }

            byteArray.add(fieldByte);

        }
        byte[] primitiveByteArray = new byte[byteArray.size()];
        for (int i = 0; i < byteArray.size(); i++) {
            primitiveByteArray[i] = byteArray.get(i);
        }

        Checksum checksum = new CRC32();
        ((CRC32) checksum).update(primitiveByteArray);


        return checksum.getValue();

    }

    @Override
    public String toString() {
        StringBuilder output = new StringBuilder();
        output.append("Container type: ");
        output.append(this.getClass().getSimpleName());
        for (Field field : this.getClass().getDeclaredFields()) {
            try {
                output.append("\n\t");
                output.append(field.getName());
                output.append(":\t");
                output.append(field.get(this));
            } catch (IllegalAccessException e) {
                log.error("Could not read from field {}", field.getName(), e);
            }
        }
        return output.toString();
    }
}
