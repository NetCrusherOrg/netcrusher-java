package org.netcrusher;

public interface NetFreezer {

    /**
     * Freezes all activity on the component. Sockets remain open
     * @see NetFreezer#unfreeze()
     * @see NetFreezer#isFrozen()
     */
    void freeze();

    /**
     * Unfreezes activity on component
     * @see NetFreezer#freeze()
     * @see NetFreezer#isFrozen()
     */
    void unfreeze();

    /**
     * Checks is the component frozen
     * @return Return <em>true</em> if the crusher is frozen (or is closed)
     * @throws IllegalStateException Throwed if the component is not open
     * @see NetFreezer#freeze()
     * @see NetFreezer#unfreeze()
     */
    boolean isFrozen();

}
