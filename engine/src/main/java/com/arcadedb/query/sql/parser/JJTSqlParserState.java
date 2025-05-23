/*
 * Copyright © 2021-present Arcade Data Ltd (info@arcadedata.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-FileCopyrightText: 2021-present Arcade Data Ltd (info@arcadedata.com)
 * SPDX-License-Identifier: Apache-2.0
 */
/* Generated By:JavaCC: Do not edit this line. JJTSqlParserState.java Version 5.0 */
package com.arcadedb.query.sql.parser;

import javax.annotation.processing.Generated;

@Generated("JavaCC")
public class JJTSqlParserState {
  private final java.util.List<Node>    nodes;
  private final java.util.List<Integer> marks;
  private       int                     sp;        // number of nodes on stack
  private       int                     mk;        // current mark

  public JJTSqlParserState() {
    nodes = new java.util.ArrayList<Node>();
    marks = new java.util.ArrayList<Integer>();
    sp = 0;
    mk = 0;
  }

  /* Call this to reinitialize the node stack.  It is called
     automatically by the parser's ReInit() method. */
  public void reset() {
    nodes.clear();
    marks.clear();
    sp = 0;
    mk = 0;
  }

  /* Pushes a node on to the stack. */
  public void pushNode(Node n) {
    nodes.add(n);
    ++sp;
  }

  /* Returns the node on the top of the stack, and remove it from the
     stack.  */
  public Node popNode() {
    if (--sp < mk) {
      mk = marks.removeLast();
    }
    return nodes.removeLast();
  }

  /* Returns the number of children on the stack in the current node
     scope. */
  public int nodeArity() {
    return sp - mk;
  }

  public void clearNodeScope(Node n) {
    while (sp > mk) {
      popNode();
    }
    mk = marks.removeLast();
  }

  public void openNodeScope(Node n) {
    marks.add(mk);
    mk = sp;
    n.jjtOpen();
  }

  /* A conditional node is constructed if its condition is true.  All
     the nodes that have been pushed since the node was opened are
     made children of the conditional node, which is then pushed
     on to the stack.  If the condition is false the node is not
     constructed and they are left on the stack. */
  public void closeNodeScope(Node n, boolean condition) {
    if (condition) {
      int a = nodeArity();
      mk = marks.removeLast();
      while (a-- > 0) {
        Node c = popNode();
        c.jjtSetParent(n);
        n.jjtAddChild(c, a);
      }
      n.jjtClose();
      pushNode(n);
    } else {
      mk = marks.removeLast();
    }
  }
}
/* JavaCC - OriginalChecksum=3b69c40f40384d155d008b16814551c5 (do not edit this line) */
