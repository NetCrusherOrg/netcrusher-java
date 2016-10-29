package org.netcrusher;

import java.io.IOException;

public interface NetFreezer {

    /**
     * Freezes all activity on the component. Sockets remain open
     * @throws IOException Throwed on IO problems
     * @see NetFreezer#unfreeze()
     * @see NetFreezer#isFrozen()
     */
    void freeze() throws IOException;

    /**
     * Unfreezes activity on component
     * @throws IOException Throwed on IO problems
     * @throws IllegalStateException Throwed if the component is not open
     * @see NetFreezer#freeze()
     * @see NetFreezer#isFrozen()
     */
    void unfreeze() throws IOException;

    /**
     * Checks is the component frozen
     * @return Return <em>true</em> if the crusher is frozen (or is closed)
     * @throws IllegalStateException Throwed if the component is not open
     * @see NetFreezer#freeze()
     * @see NetFreezer#unfreeze()
     */
    boolean isFrozen();

}
